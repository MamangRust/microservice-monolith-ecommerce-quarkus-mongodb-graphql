package com.sanedge.slider.repository;

import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import com.sanedge.common.domain.response.PagedResult;
import com.sanedge.slider.domain.requests.FindAllSliderRequest;
import com.sanedge.slider.entity.Slider;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoRepository;
import io.quarkus.mongodb.panache.reactive.ReactivePanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SliderQueryRepository implements ReactivePanacheMongoRepository<Slider> {

    public Uni<PagedResult<Slider>> findSliders(FindAllSliderRequest req) {
        return findSlidersInternal(req, false);
    }

    public Uni<PagedResult<Slider>> findActiveSliders(FindAllSliderRequest req) {
        return findSlidersInternal(req, false);
    }

    public Uni<PagedResult<Slider>> findTrashedSliders(FindAllSliderRequest req) {
        return findSlidersInternal(req, true);
    }

    private Uni<PagedResult<Slider>> findSlidersInternal(FindAllSliderRequest req, boolean trashed) {
        int page = req.getPage() > 0 ? req.getPage() - 1 : 0;
        int size = req.getPageSize() > 0 ? req.getPageSize() : 10;
        String keyword = (req.getSearch() != null && !req.getSearch().trim().isEmpty()) ? req.getSearch().trim() : null;

        ReactivePanacheQuery<Slider> panacheQuery;
        if (trashed) {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': { '$ne': null } }")
                    : find("{ 'deletedAt': { '$ne': null }, 'name': { '$regex': ?1, '$options': 'i' } }",
                            Pattern.quote(keyword));
        } else {
            panacheQuery = keyword == null
                    ? find("{ 'deletedAt': null }")
                    : find("{ 'deletedAt': null, 'name': { '$regex': ?1, '$options': 'i' } }",
                            Pattern.quote(keyword));
        }
        panacheQuery.page(page, size);

        return Uni.combine().all().unis(panacheQuery.list(), panacheQuery.count())
                .asTuple()
                .map(tuple -> new PagedResult<>(tuple.getItem1(), tuple.getItem2().intValue()));
    }

    public Uni<Slider> findById(String sliderId) {
        return find("_id", new ObjectId(sliderId)).firstResult();
    }
}
