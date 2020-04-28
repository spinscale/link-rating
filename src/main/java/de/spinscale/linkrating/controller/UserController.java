/*
 * Copyright [2020] [Alexander Reelsen]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package de.spinscale.linkrating.controller;

import de.spinscale.linkrating.AdminService;
import de.spinscale.linkrating.entity.Link;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.DistanceFeatureQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RankFeatureQueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping(path = "/")
public class UserController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private ElasticsearchOperations elasticsearchRestTemplate;

    @Inject
    public UserController(ElasticsearchOperations elasticsearchTemplate, AdminService adminService) {
        super(adminService.get());
        this.elasticsearchRestTemplate = elasticsearchTemplate;
    }

    @GetMapping
    public String main(@AuthenticationPrincipal OAuth2User principal,
                       @RequestParam(value = "q", required = false) final String q,
                       final Model model) {
        try {
            final BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("approved", true))
                    .should(QueryBuilders.distanceFeatureQuery("created_at", new DistanceFeatureQueryBuilder.Origin("now"), "7d"))
                    .should(RankFeatureQueryBuilders.saturation("votes.rank"));
            if (Strings.hasLength(q)) {
                queryBuilder.must(QueryBuilders.multiMatchQuery(q, "title", "description").minimumShouldMatch("66%"));
            }
            Query query = new NativeSearchQuery(queryBuilder);
            model.addAttribute("links", search(query));
        } catch (Exception e) {
            logger.error("error querying for [" + q + "]", e);
            model.addAttribute("links", Collections.<Link>emptyList());
        }
        model.addAttribute("q", q);
        enrichModelWithPrincipal(model, principal);
        return "main";
    }

    // list unapproved links, this should only be reachable by an admin
    @GetMapping("unapproved")
    public String showUnapproved(@AuthenticationPrincipal OAuth2User principal, final Model model) {
        ensureAdmin(principal);
        enrichModelWithPrincipal(model, principal);

        Query query = new NativeSearchQuery(QueryBuilders.termQuery("approved", false))
                .setPageable(PageRequest.of(0, 50))
                .addSort(Sort.by(new Sort.Order(Sort.Direction.DESC, "created_at")));

        model.addAttribute("links", search(query));

        return "main";
    }

    private List<Link> search(Query query) {
        final SearchHits<Link> result = elasticsearchRestTemplate.search(query, Link.class);
        if (result.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Link> links = result.getSearchHits().stream().map(SearchHit::getContent).collect(Collectors.toList());
        return links;
    }
}