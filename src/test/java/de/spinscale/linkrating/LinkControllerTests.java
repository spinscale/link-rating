package de.spinscale.linkrating;

import de.spinscale.linkrating.controller.LinkController;
import de.spinscale.linkrating.entity.Link;
import de.spinscale.linkrating.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkControllerTests {

    private ElasticsearchOperations elasticsearchOperations = mock(ElasticsearchOperations.class);
    private final LinkController controller = new LinkController(elasticsearchOperations, new AdminService("admin"));
    private final Model model = new ExtendedModelMap();

    @Test
    public void testShowApprovedLinkWithoutLogin() {
        Link link = new Link();
        link.setId("my_id");
        link.setApproved(true);
        when(elasticsearchOperations.get(eq("my_id"), eq(Link.class))).thenReturn(link);

        String result = controller.show(null, "my_id", model);

        assertThat(result).isEqualTo("main");
        assertModelContainsLink(link);
    }

    @Test
    public void testShowNonExistingLinksThrows404() {
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.show(null, "my_id", model))
                .withMessage("404 NOT_FOUND");
    }

    @Test
    public void testShowAdminUnapprovedLink() {
        Link link = new Link();
        link.setId("my_id");
        link.setApproved(false);
        when(elasticsearchOperations.get(eq("my_id"), eq(Link.class))).thenReturn(link);

        String result = controller.show(createUser("admin"), "my_id", model);
        assertThat(result).isEqualTo("main");
        assertModelContainsLink(link);
    }

    @Test
    public void testShowUnapprovedLinkShouldBeNotFoundForUser() {
        Link link = new Link();
        link.setId("my_id");
        link.setApproved(false);
        when(elasticsearchOperations.get(eq("my_id"), eq(Link.class))).thenReturn(link);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.show(createUser("user"), "my_id", model))
                .withMessage("404 NOT_FOUND");
    }

    @Test
    public void testSubmitLink() {
        OAuth2User principal = createUser("user");

        String result = controller.submitLink(principal, "description", "<b>Title</b>", "http://example.org", "Category");

        assertThat(result).isEqualTo("redirect:/");

        ArgumentCaptor linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(elasticsearchOperations).save(linkCaptor.capture());
        Link link = (Link) linkCaptor.getValue();
        assertThat(link.getDescription()).isEqualTo("description");
        assertThat(link.getCategory()).isEqualTo("category");
        assertThat(link.getUrl()).isEqualTo("http://example.org");
        assertThat(link.getTitle()).isEqualTo("Title");
        assertThat(link.isApproved()).isEqualTo(false);
        assertThat(link.getSubmittedBy()).isEqualTo("user");
    }

    @Test
    public void testSubmitExistingApprovedLinkRedirectsToLink() {
        Link link = new Link();
        link.setId("123");
        link.setUrl("http://example.org");
        link.setApproved(true);
        final SearchHit<Link> searchHit = mock(SearchHit.class);
        when(searchHit.getContent()).thenReturn(link);
        when(elasticsearchOperations.searchOne(any(), eq(Link.class))).thenReturn(searchHit);

        OAuth2User principal = createUser("user");
        String result = controller.submitLink(principal, "description", "<b>Title</b>", "http://example.org", "Category");

        assertThat(result).isEqualTo("redirect:/link/123");
    }

    @Test
    public void testSubmitExistingUnApprovedLinkRedirectsToMain() {
        Link link = new Link();
        link.setId("123");
        link.setUrl("http://example.org");
        link.setApproved(false);
        final SearchHit<Link> searchHit = mock(SearchHit.class);
        when(searchHit.getContent()).thenReturn(link);
        when(elasticsearchOperations.searchOne(any(), eq(Link.class))).thenReturn(searchHit);

        OAuth2User principal = createUser("user");
        String result = controller.submitLink(principal, "description", "<b>Title</b>", "http://example.org", "Category");

        assertThat(result).isEqualTo("redirect:/");
    }

    @Test
    public void testUserCannotSubmitMoreThanTenLinks() {
        when(elasticsearchOperations.count(any(), eq(Link.class))).thenReturn(11L);
        OAuth2User principal = createUser("user");

        String result = controller.submitLink(principal, "description", "<b>Title</b>", "http://example.org", "Category");

        assertThat(result).isEqualTo("redirect:/");
        verify(elasticsearchOperations, never()).save(any(Link.class));
    }

    @Test
    public void testDeleteAsAdmin() {
        String result = controller.delete(createUser("admin"), "123");
        assertThat(result).isEqualTo("redirect:/");
        verify(elasticsearchOperations).delete(eq("123"), eq(Link.class));
    }

    @Test
    public void testDeleteAsUserThrowsException() {
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.delete(createUser("user"), "123"))
                .withMessage("404 NOT_FOUND");
    }

    @Test
    public void testApproveAsAdmin() {
        String result = controller.approve(createUser("admin"), "123");
        assertThat(result).isEqualTo("redirect:/unapproved");
        ArgumentCaptor<UpdateQuery> updateCaptor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(updateCaptor.capture(), any());
        final UpdateQuery updateQuery = updateCaptor.getValue();
        assertThat(updateQuery.getId()).isEqualTo("123");
    }

    @Test
    public void testApproveAsUserThrowsException() {
        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.approve(createUser("user"), "123"))
                .withMessage("404 NOT_FOUND");
    }

    @Test
    public void testVoteUserDoesNotExist() {
        OAuth2User principal = createUser("user");
        when(elasticsearchOperations.get(eq("user"), eq(User.class))).thenReturn(null);

        String result = controller.vote(principal, "123", null);
        assertThat(result).isEqualTo("redirect:/");

        assertUserVoteAndLinkVote("user", "123");
    }

    @Test
    public void testVoteUserExists() {
        OAuth2User principal = createUser("user");
        User existingUser = new User();
        existingUser.setId("user");
        existingUser.setIds(new ArrayList<>());
        when(elasticsearchOperations.get(eq("user"), eq(User.class))).thenReturn(existingUser);

        String result = controller.vote(principal, "123", null);
        assertThat(result).isEqualTo("redirect:/");

        assertUserVoteAndLinkVote("user", "123");
    }

    @Test
    public void testVoteUserHasAlreadyVotedForAnotherLink() {
        OAuth2User principal = createUser("user");
        User existingUser = new User();
        existingUser.setId("user");
        List<String> ids = new ArrayList<>();
        ids.add("456");
        existingUser.setIds(ids);
        when(elasticsearchOperations.get(eq("user"), eq(User.class))).thenReturn(existingUser);

        String result = controller.vote(principal, "123", null);
        assertThat(result).isEqualTo("redirect:/");

        assertUserVoteAndLinkVote("user", "123", "456");
    }

    @Test
    public void testVoteUserHasAlreadyVotedForThisLink() {
        OAuth2User principal = createUser("user");
        User existingUser = new User();
        existingUser.setId("user");
        List<String> ids = new ArrayList<>();
        ids.add("123");
        existingUser.setIds(ids);
        when(elasticsearchOperations.get(eq("user"), eq(User.class))).thenReturn(existingUser);

        String result = controller.vote(principal, "123", null);
        assertThat(result).isEqualTo("redirect:/");

        verify(elasticsearchOperations, never()).save(any(User.class));;
        verify(elasticsearchOperations, never()).update(any(), any());;
    }

    @Test
    public void testVoteUserIsReferredToLinkPage() {
        OAuth2User principal = createUser("user");
        User existingUser = new User();
        existingUser.setId("user");
        existingUser.setIds(new ArrayList<>());
        when(elasticsearchOperations.get(eq("user"), eq(User.class))).thenReturn(existingUser);

        String result = controller.vote(principal, "123", "/link/123");
        assertThat(result).isEqualTo("redirect:/link/123");
    }

    private void assertUserVoteAndLinkVote(String username, String votedLinkId, String ... votedLinkIds) {
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(elasticsearchOperations).save(userCaptor.capture());
        ArgumentCaptor<UpdateQuery> updateCaptor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(updateCaptor.capture(), any());

        User user = userCaptor.getValue();
        assertThat(user.getId()).isEqualTo(username);
        if (votedLinkIds.length == 0) {
            assertThat(user.getIds()).containsOnly(votedLinkId);
        } else {
            assertThat(user.getIds()).contains(votedLinkId);
            assertThat(user.getIds()).contains(votedLinkIds);
        }

        UpdateQuery updateQuery = updateCaptor.getValue();
        assertThat(updateQuery.getId()).isEqualTo(votedLinkId);

    }

    static OAuth2User createUser(String githubLogin) {
        final OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute(eq("login"))).thenReturn(githubLogin);
        return principal;
    }

    private void assertModelContainsLink(Link link) {
        assertThat(model.asMap()).containsKey("links");
        List<Link> links = (List<Link>) model.asMap().get("links");
        assertThat(links).hasSize(1);
        assertThat(links).containsOnly(link);
    }
}
