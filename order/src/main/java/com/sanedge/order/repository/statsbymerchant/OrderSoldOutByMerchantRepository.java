package com.sanedge.order.repository.statsbymerchant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.sanedge.order.entity.Order;
import com.sanedge.order.entity.OrderMonthly;
import com.sanedge.order.entity.OrderYearly;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderSoldOutByMerchantRepository implements ReactivePanacheMongoRepository<Order> {

    private static final String[] MONTH_NAMES = { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

    private static Date dateOf(int year, int month) {
        return Date.from(Instant.parse(String.format("%04d-%02d-01T00:00:00Z", year, month)));
    }

    private List<Bson> basePipeline(Date start, Date end, ObjectId merchantId) {
        List<Bson> matchFilters = new ArrayList<>();
        matchFilters.add(Filters.eq("deletedAt", null));
        matchFilters.add(Filters.gte("createdAt", start));
        matchFilters.add(Filters.lt("createdAt", end));
        matchFilters.add(Filters.eq("merchantId", merchantId));

        List<Bson> stages = new ArrayList<>();
        stages.add(Aggregates.match(Filters.and(matchFilters)));
        stages.add(Aggregates.lookup("order_items", "_id", "orderId", "items"));
        stages.add(Aggregates.unwind("$items"));
        stages.add(Aggregates.match(Filters.eq("items.deletedAt", null)));
        return stages;
    }

    private Uni<List<Document>> runAggregate(List<Bson> pipeline) {
        return mongoCollection().aggregate(pipeline, Document.class).collect().asList();
    }

    private static int size(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    public Uni<List<OrderMonthly>> findMonthlyOrdersByMerchant(
            com.sanedge.order.domain.requests.MonthOrderMerchantRequest req) {
        ObjectId merchantId = new ObjectId(req.getMerchantId());
        Integer year = req.getYear();
        Integer month = req.getMonth();

        LocalDate currentMonth = LocalDate.of(year, month, 1);
        LocalDate prevMonth = currentMonth.minusMonths(1);

        Date start = dateOf(prevMonth.getYear(), prevMonth.getMonthValue());
        Date end = dateOf(currentMonth.plusMonths(1).getYear(), currentMonth.plusMonths(1).getMonthValue());

        List<Bson> pipeline = basePipeline(start, end, merchantId);
        Document groupId = new Document("monthIndex",
                new Document("$add", List.of(new Document("$multiply", List.of(new Document("$year", "$createdAt"), 100)),
                        new Document("$month", "$createdAt"))))
                .append("month",
                        new Document("$dateToString", new Document("format", "%b").append("date", "$createdAt")));
        pipeline.add(Aggregates.group(groupId,
                Accumulators.addToSet("orderIds", "$_id"),
                Accumulators.sum("totalRevenue", "$totalPrice"),
                Accumulators.sum("itemsSold", "$items.quantity")));

        return runAggregate(pipeline).map(docs -> {
            Map<Integer, OrderMonthly> byIndex = new LinkedHashMap<>();
            for (Document doc : docs) {
                Document id = doc.get("_id", Document.class);
                int index = ((Number) id.get("monthIndex", 0)).intValue();
                byIndex.put(index, new OrderMonthly(
                        id.getString("month"),
                        size(doc.get("orderIds")),
                        ((Number) doc.getOrDefault("totalRevenue", 0)).longValue(),
                        ((Number) doc.getOrDefault("itemsSold", 0)).intValue()));
            }
            for (LocalDate d : List.of(prevMonth, currentMonth)) {
                int index = d.getYear() * 100 + d.getMonthValue();
                byIndex.computeIfAbsent(index,
                        i -> new OrderMonthly(MONTH_NAMES[d.getMonthValue() - 1], 0, 0L, 0));
            }
            List<Integer> sorted = new ArrayList<>(byIndex.keySet());
            sorted.sort(Integer::compareTo);
            List<OrderMonthly> result = new ArrayList<>();
            for (Integer index : sorted) {
                result.add(byIndex.get(index));
            }
            return result;
        });
    }

    public Uni<List<OrderYearly>> findYearlyOrdersByMerchant(String merchantId, Integer year) {
        List<Bson> pipeline = basePipeline(dateOf(year - 4, 1), dateOf(year + 1, 1), new ObjectId(merchantId));
        Document groupId = new Document("year",
                new Document("$dateToString", new Document("format", "%Y").append("date", "$createdAt")));
        pipeline.add(Aggregates.group(groupId,
                Accumulators.addToSet("orderIds", "$_id"),
                Accumulators.sum("totalRevenue", "$totalPrice"),
                Accumulators.sum("itemsSold", "$items.quantity"),
                Accumulators.addToSet("cashierIds", "$userId"),
                Accumulators.addToSet("productIds", "$items.productId")));

        return runAggregate(pipeline).map(docs -> {
            Map<Integer, OrderYearly> byYear = new LinkedHashMap<>();
            for (Document doc : docs) {
                Document id = doc.get("_id", Document.class);
                int y = Integer.parseInt(id.getString("year"));
                byYear.put(y, new OrderYearly(
                        id.getString("year"),
                        size(doc.get("orderIds")),
                        ((Number) doc.getOrDefault("totalRevenue", 0)).longValue(),
                        ((Number) doc.getOrDefault("itemsSold", 0)).intValue(),
                        size(doc.get("cashierIds")),
                        size(doc.get("productIds"))));
            }
            for (int y = year - 4; y <= year; y++) {
                byYear.computeIfAbsent(y, i -> new OrderYearly(String.valueOf(i), 0, 0L, 0, 0, 0));
            }
            List<Integer> sorted = new ArrayList<>(byYear.keySet());
            sorted.sort(Integer::compareTo);
            List<OrderYearly> result = new ArrayList<>();
            for (Integer y : sorted) {
                result.add(byYear.get(y));
            }
            return result;
        });
    }
}
