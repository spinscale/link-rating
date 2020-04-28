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

package de.spinscale.linkrating;

import de.spinscale.linkrating.controller.UserController;
import de.spinscale.linkrating.entity.Link;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.spinscale.linkrating.LinkControllerTests.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserControllerTests {

    private ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final UserController controller = new UserController(elasticsearchOperations, new AdminService("admin"));
    private final Model model = new ExtendedModelMap();

    @Test
    public void testMainPage() {
        mockSearchResponse();

        String result = controller.main(null, null, model);
        assertThat(result).isEqualTo("main");
        assertThat(model.asMap()).containsKey("links");
        final List<Link> links = (List<Link>) model.asMap().get("links");
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getId()).isEqualTo("my_id");
    }

    @Test
    public void testMainPageWithException() {
        String result = controller.main(null, null, model);
        assertThat(result).isEqualTo("main");
        assertThat(model.asMap()).containsEntry("links", Collections.emptyList());
    }

    @Test
    public void testMainPageWithQuery() {
        controller.main(null, "my query", model);

        // ensure model is enriched
        assertThat(model.asMap()).containsEntry("q", "my query");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(elasticsearchOperations).search(queryCaptor.capture(), any(Class.class));
        final NativeSearchQuery query = (NativeSearchQuery) queryCaptor.getValue();
        assertThat(query.getQuery()).isInstanceOf(BoolQueryBuilder.class);

        // ensure query is sent with second must clause
        BoolQueryBuilder queryBuilder = (BoolQueryBuilder) query.getQuery();
        assertThat(queryBuilder.must()).hasSize(2);
        assertThat(queryBuilder.must().get(1)).isInstanceOf(MultiMatchQueryBuilder.class);
        MultiMatchQueryBuilder multiMatchQueryBuilder = (MultiMatchQueryBuilder) queryBuilder.must().get(1);
        assertThat(multiMatchQueryBuilder.value()).isEqualTo("my query");
    }

    @Test
    public void testUnapproved() {
        mockSearchResponse();

        String result = controller.showUnapproved(createUser("admin"), model);
        assertThat(result).isEqualTo("main");
        assertThat(model.asMap()).containsKey("links");
        final List<Link> links = (List<Link>) model.asMap().get("links");
        assertThat(links).hasSize(1);
    }

    @Test
    public void testUnapprovedRequiresAdmin() {
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.showUnapproved(null, model))
                .withMessage("404 NOT_FOUND");

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.showUnapproved(createUser("user"), model))
                .withMessage("404 NOT_FOUND");
    }

    private void mockSearchResponse() {
        SearchHits<Link> searchHits = mock(SearchHits.class);
        when(searchHits.isEmpty()).thenReturn(false);
        List<SearchHit<Link>> hits = new ArrayList<>();
        Link link = new Link();
        link.setId("my_id");
        hits.add(new SearchHit<>(link.getId(), 1.0f, null, null, link));
        when(searchHits.getSearchHits()).thenReturn(hits);
        when(elasticsearchOperations.search(any(Query.class), any(Class.class))).thenReturn(searchHits);
    }
}
