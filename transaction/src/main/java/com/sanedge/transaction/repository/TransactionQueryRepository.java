package com.sanedge.transaction.repository;

import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.transaction.domain.requests.FindAllTransactionByMerchantRequest;
import com.sanedge.transaction.domain.requests.FindAllTransactionRequest;
import com.sanedge.transaction.entity.Transaction;
import com.sanedge.transaction.enums.PaymentStatus;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionQueryRepository implements ReactivePanacheMongoRepository<Transaction> {

    private int pageIndex(int page) {
        return page > 0 ? page - 1 : 0;
    }

    private int pageSize(int size) {
        return size > 0 ? size : 10;
    }

    private String keyword(String search) {
        return (search != null && !search.trim().isEmpty()) ? search.trim() : null;
    }

    private Uni<PagedResult<Transaction>> pagedResult(ReactivePanacheQuery<Transaction> panacheQuery) {
        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(t -> new PagedResult<>(t.getItem1(), t.getItem2().intValue()));
    }

    public Uni<PagedResult<Transaction>> findTransactions(FindAllTransactionRequest req) {
        String keyword = keyword(req.getSearch());

        ReactivePanacheQuery<Transaction> panacheQuery = keyword == null
                ? find("{ 'deletedAt': null }")
                : find("{ 'deletedAt': null, 'paymentMethod': { '$regex': ?1, '$options': 'i' } }",
                        Pattern.quote(keyword));

        return pagedResult(panacheQuery.page(pageIndex(req.getPage()), pageSize(req.getPageSize())));
    }

    public Uni<PagedResult<Transaction>> findActiveTransactions(FindAllTransactionRequest req) {
        return findTransactions(req);
    }

    public Uni<PagedResult<Transaction>> findTrashedTransactions(FindAllTransactionRequest req) {
        String keyword = keyword(req.getSearch());

        ReactivePanacheQuery<Transaction> panacheQuery = keyword == null
                ? find("{ 'deletedAt': { '$ne': null } }")
                : find("{ 'deletedAt': { '$ne': null }, 'paymentMethod': { '$regex': ?1, '$options': 'i' } }",
                        Pattern.quote(keyword));

        return pagedResult(panacheQuery.page(pageIndex(req.getPage()), pageSize(req.getPageSize())));
    }

    public Uni<PagedResult<Transaction>> findTransactionsByMerchant(FindAllTransactionByMerchantRequest req) {
        ObjectId merchantId = (req.getMerchantId() != null && !req.getMerchantId().isBlank())
                ? new ObjectId(req.getMerchantId())
                : null;
        String keyword = keyword(req.getSearch());

        ReactivePanacheQuery<Transaction> panacheQuery;
        if (merchantId == null && keyword == null) {
            panacheQuery = find("{ 'deletedAt': null }");
        } else if (keyword == null) {
            panacheQuery = find("{ 'deletedAt': null, 'merchantId': ?1 }", merchantId);
        } else if (merchantId == null) {
            panacheQuery = find("{ 'deletedAt': null, 'paymentMethod': { '$regex': ?1, '$options': 'i' } }",
                    Pattern.quote(keyword));
        } else {
            panacheQuery = find(
                    "{ 'deletedAt': null, 'merchantId': ?1, 'paymentMethod': { '$regex': ?2, '$options': 'i' } }",
                    merchantId, Pattern.quote(keyword));
        }

        return pagedResult(panacheQuery.page(pageIndex(req.getPage()), pageSize(req.getPageSize())));
    }

    public Uni<Optional<Transaction>> findTransactionById(String transactionId) {
        return find("{ '_id': ?1, 'deletedAt': null }", new ObjectId(transactionId)).firstResult()
                .map(Optional::ofNullable);
    }

    public Uni<Optional<Transaction>> findByOrderId(String orderId) {
        return find("{ 'orderId': ?1, 'deletedAt': null }", new ObjectId(orderId)).firstResult()
                .map(Optional::ofNullable);
    }

    public Uni<Optional<Transaction>> findActivePaymentByOrderId(String orderId) {
        return find("{ 'orderId': ?1, 'deletedAt': null, 'status': { '$ne': ?2 } }",
                new ObjectId(orderId), PaymentStatus.FAILED.name()).firstResult()
                .map(Optional::ofNullable);
    }

    public Uni<Transaction> findById(String transactionId) {
        return find("_id", new ObjectId(transactionId)).firstResult();
    }
}
