package com.sanedge.merchant_detail.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.merchant_detail.entity.MerchantDetail;
import com.sanedge.merchant_detail.entity.MerchantDetailsRelation;
import com.sanedge.merchant_detail.entity.MerchantSocialMediaLink;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MerchantDetailQueryRepository
        implements ReactivePanacheMongoRepository<MerchantDetail>, MerchantDetailQueryRepositoryCustom {

    private static final String MERCHANT_COLLECTION = "merchants";
    private static final String SOCIAL_LINK_COLLECTION = "merchant_social_media_links";

    public Uni<MerchantDetail> findById(String merchantDetailId) {
        return find("_id", new ObjectId(merchantDetailId)).firstResult();
    }

    @Override
    public Uni<PagedResult<MerchantDetailsRelation>> findAllWithSocialLinks(com.sanedge.merchant_detail.domain.requests.FindAllMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        return queryWithSocialLinks(search, null, null, page, size);
    }

    @Override
    public Uni<PagedResult<MerchantDetailsRelation>> findActiveWithSocialLinks(com.sanedge.merchant_detail.domain.requests.FindAllMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        return queryWithSocialLinks(search, true, null, page, size);
    }

    @Override
    public Uni<PagedResult<MerchantDetailsRelation>> findTrashedWithSocialLinks(com.sanedge.merchant_detail.domain.requests.FindAllMerchantRequest req) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String search = (req.getSearch() != null && !req.getSearch().isEmpty()) ? req.getSearch() : "";
        return queryWithSocialLinks(search, false, null, page, size);
    }

    @Override
    public Uni<Optional<MerchantDetailsRelation>> findByIdWithSocialLinks(String merchantDetailId) {
        return queryWithSocialLinks(null, null, merchantDetailId, 0, 1)
                .map(paged -> {
                    if (paged.getData().isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(paged.getData().get(0));
                });
    }

    /**
     * The PostgreSQL native join to {@code merchants} (for the merchant name search
     * and the merchant active/trashed status) and to
     * {@code merchant_social_media_links} is rewritten as a MongoDB aggregation
     * using {@code $lookup}.
     */
    private Uni<PagedResult<MerchantDetailsRelation>> queryWithSocialLinks(String keyword, Boolean isActive, String id,
            int page, int size) {
        List<Bson> conditions = new ArrayList<>();
        if (keyword != null && !keyword.isEmpty()) {
            conditions.add(Filters.regex("merchant.name", Pattern.quote(keyword), "i"));
        }
        if (isActive != null) {
            conditions.add(isActive ? Filters.eq("merchant.deletedAt", null) : Filters.ne("merchant.deletedAt", null));
        }
        if (id != null) {
            conditions.add(Filters.eq("_id", new ObjectId(id)));
        }
        Bson filter = conditions.isEmpty() ? new Document() : Filters.and(conditions);

        Bson merchantLookup = Aggregates.lookup(MERCHANT_COLLECTION, "merchantId", "_id", "merchant");
        Bson unwindMerchant = Aggregates.unwind("$merchant");
        Bson socialLookup = Aggregates.lookup(SOCIAL_LINK_COLLECTION, "_id", "merchantDetailId", "socialMediaLinks");

        List<Bson> dataPipeline = List.of(
                merchantLookup,
                unwindMerchant,
                socialLookup,
                Aggregates.match(filter),
                Aggregates.sort(Sorts.descending("createdAt")),
                Aggregates.skip(page * size),
                Aggregates.limit(size));

        List<Bson> countPipeline = List.of(
                merchantLookup,
                unwindMerchant,
                Aggregates.match(filter),
                Aggregates.count("total"));

        Uni<List<MerchantDetailsRelation>> dataUni = mongoCollection()
                .aggregate(dataPipeline, Document.class)
                .collect().asList()
                .map(docs -> docs.stream().map(this::mapDocument).collect(Collectors.toList()));

        Uni<Long> countUni = mongoCollection()
                .aggregate(countPipeline, Document.class)
                .collect().asList()
                .map(list -> list.isEmpty() ? 0L : ((Number) list.get(0).get("total")).longValue());

        return Uni.combine().all().unis(dataUni, countUni)
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    private MerchantDetailsRelation mapDocument(Document doc) {
        MerchantDetailsRelation dto = new MerchantDetailsRelation();
        dto.setId(toHex(doc.getObjectId("_id")));
        dto.setMerchantId(toHex(doc.getObjectId("merchantId")));
        dto.setDisplayName(doc.getString("displayName"));
        dto.setCoverImageUrl(doc.getString("coverImageUrl"));
        dto.setLogoUrl(doc.getString("logoUrl"));
        dto.setShortDescription(doc.getString("shortDescription"));
        dto.setWebsiteUrl(doc.getString("websiteUrl"));
        dto.setCreatedAt(toIsoString(doc.get("createdAt")));
        dto.setUpdatedAt(toIsoString(doc.get("updatedAt")));
        dto.setDeletedAt(toIsoString(doc.get("deletedAt")));

        List<MerchantSocialMediaLink> links = new ArrayList<>();
        List<Document> linkDocs = doc.getList("socialMediaLinks", Document.class);
        if (linkDocs != null) {
            for (Document ld : linkDocs) {
                MerchantSocialMediaLink link = new MerchantSocialMediaLink();
                link.id = ld.getObjectId("_id");
                link.setMerchantDetailId(ld.getObjectId("merchantDetailId"));
                link.setPlatform(ld.getString("platform"));
                link.setUrl(ld.getString("url"));
                link.setCreatedAt(toInstant(ld.get("createdAt")));
                link.setUpdatedAt(toInstant(ld.get("updatedAt")));
                links.add(link);
            }
        }
        dto.setSocialMediaLinks(links);
        return dto;
    }

    private static String toHex(ObjectId id) {
        return id != null ? id.toHexString() : null;
    }

    private static String toIsoString(Object value) {
        Instant instant = toInstant(value);
        return instant != null ? instant.toString() : null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }
        return Instant.parse(value.toString());
    }
}
