package de.spinscale.linkrating.controller;

import de.spinscale.linkrating.AdminService;
import de.spinscale.linkrating.entity.Link;
import de.spinscale.linkrating.entity.User;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Date;

@Controller
@RequestMapping(path = "/link")
public class LinkController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(LinkController.class);

    private final ElasticsearchOperations elasticsearchRestTemplate;

    @Inject
    public LinkController(ElasticsearchOperations elasticsearchTemplate, AdminService adminService) {
        super(adminService.get());
        this.elasticsearchRestTemplate = elasticsearchTemplate;
    }

    // check out single entry
    @GetMapping(path = "{id}")
    public String show(@AuthenticationPrincipal final OAuth2User principal,
                       @PathVariable("id") final String id,
                       final Model model) {
        final Link link = elasticsearchRestTemplate.get(id, Link.class);
        // only admin can see an unapproved link!
        if (link != null && (link.isApproved() || isAdmin(principal))) {
            model.addAttribute("links", Collections.singletonList(link));
        } else {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        enrichModelWithPrincipal(model, principal);
        return "main";
    }


    @PostMapping
    public String submitLink(@AuthenticationPrincipal final OAuth2User principal,
                             @RequestParam("description") final String description,
                             @RequestParam("title") final String title,
                             @RequestParam("url") final String url,
                             @RequestParam("category") final String category) {

        final TermQueryBuilder qb = QueryBuilders.termQuery("url", url);
        final SearchHit<Link> hit = elasticsearchRestTemplate.searchOne(new NativeSearchQuery(qb), Link.class);
        if (hit == null) {
            final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("submitted_by", principal.getAttribute("login")))
                    .must(QueryBuilders.termQuery("approved", false));
            final long linksSubmittedByUser = elasticsearchRestTemplate.count(new NativeSearchQuery(boolQueryBuilder), Link.class);
            if (linksSubmittedByUser < 10) {
                Link link = new Link();
                link.setCreatedAt(new Date());
                link.setDescription(description);
                link.setTitle(title);
                link.setUrl(url);
                link.setCategory(category);
                link.setVotes(1L);
                link.setApproved(false);
                link.setSubmittedBy(principal.getAttribute("login"));
                elasticsearchRestTemplate.save(link);
            }
        } else {
            if (hit.getContent().isApproved()) {
                return "redirect:/link/" + hit.getContent().getId();
            }
        }

        // redirect after post baby
        return "redirect:/";
    }



    @PostMapping("{id}/vote")
    public String vote(@AuthenticationPrincipal final OAuth2User principal,
                       @PathVariable("id") final String id,
                       @RequestHeader(value = "referer", required = false) final String referer) {
        String username = principal.getAttribute("login");

        // this can be optimized A LOT by executing a count request and then run an update request for the user
        // this requires a full serialization/deserialization circle and thus is highly ineffective
        // OTOH voting will not happen super often
        User user = elasticsearchRestTemplate.get(username, User.class);
        final boolean hasUserAlreadyVoted;
        if (user == null) {
            user = new User();
            user.setId(username);
            hasUserAlreadyVoted = false;
        } else {
            hasUserAlreadyVoted = user.getIds().contains(id);
        }
        if (!hasUserAlreadyVoted) {
            // store new user or update
            if (user.getIds() == null) {
                user.setIds(Collections.singletonList(id));
            } else {
                user.getIds().add(id);
            }
            elasticsearchRestTemplate.save(user);

            // increment vote count on link via script, so that concurrent updates would work
            final UpdateQuery updateQuery = UpdateQuery.builder(id).withLang("painless").withScript("ctx._source.votes = ctx._source.votes + 1;").withRetryOnConflict(3).withRefresh(UpdateQuery.Refresh.True).build();
            elasticsearchRestTemplate.update(updateQuery, IndexCoordinates.of("links"));
        } else {
            logger.info("user [{}] tried to vote a second time for id [{}]", user.getId(), id);
        }

        // redirect after post baby
        if (referer != null && referer.contains(id)) {
            return "redirect:/link/" + id;
        } else {
            return "redirect:/";
        }
    }

    @PostMapping("{id}/delete")
    public String delete(@AuthenticationPrincipal OAuth2User principal, @PathVariable("id") final String id) {
        ensureAdmin(principal);
        // possibly we could refresh here, so that the document is missing immediately afer the refresh
        elasticsearchRestTemplate.delete(id, Link.class);
        return "redirect:/";
    }

    @PostMapping("{id}/approve")
    public String approve(@AuthenticationPrincipal OAuth2User principal, @PathVariable("id") final String id) {
        ensureAdmin(principal);

        final UpdateQuery updateQuery = UpdateQuery.builder(id)
                .withRefresh(UpdateQuery.Refresh.True)
                .withDocument(Document.from(Collections.singletonMap("approved", true)))
                .build();
        elasticsearchRestTemplate.update(updateQuery, IndexCoordinates.of("links"));

        return "redirect:/unapproved";
    }
}
