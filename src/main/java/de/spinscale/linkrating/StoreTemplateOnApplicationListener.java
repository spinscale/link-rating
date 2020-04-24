package de.spinscale.linkrating;

import de.spinscale.linkrating.entity.Link;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indexlifecycle.LifecyclePolicy;
import org.elasticsearch.client.indexlifecycle.Phase;
import org.elasticsearch.client.indexlifecycle.PutLifecyclePolicyRequest;
import org.elasticsearch.client.indexlifecycle.RolloverAction;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
public class StoreTemplateOnApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StoreTemplateOnApplicationListener.class);

    private static final String USER_MAPPING = "{\n" +
            "  \"properties\": {\n" +
            "    \"user_id\": {\n" +
            "      \"type\": \"keyword\"\n" +
            "    },\n" +
            "    \"votes\": {\n" +
            "      \"type\": \"keyword\"\n" +
            "    }\n" +
            "  }\n" +
            "}\n";

    private static final String LINKS_MAPPING = "{\n" +
            "    \"properties\": {\n" +
            "      \"title\": {\n" +
            "        \"type\": \"text\"\n" +
            "      },\n" +
            "      \"description\": {\n" +
            "        \"type\": \"text\"\n" +
            "      },\n" +
            "      \"category\": {\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"url\": {\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"created_by\": {\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"created_at\": {\n" +
            "        \"type\": \"date\"\n" +
            "      },\n" +
            "      \"votes\": {\n" +
            "        \"type\": \"long\",\n" +
            "        \"fields\": {\n" +
            "          \"rank\": {\n" +
            "            \"type\": \"rank_feature\"\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "}\n";

    @Autowired
    private RestHighLevelClient client;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Connecting to Elasticsearch cluster to write lifecycle policy, index templates, and optionally initial data");

        // store ILM policy, always overwrite on startup
        try {
            // yearly rollover is fine on low traffic...
            final RolloverAction rolloverAction = new RolloverAction(new ByteSizeValue(10, ByteSizeUnit.GB), TimeValue.timeValueDays(365), null);
            final Phase phase = new Phase("hot", TimeValue.timeValueDays(7), Collections.singletonMap("rollover", rolloverAction));
            final LifecyclePolicy policy = new LifecyclePolicy("link_policy", Collections.singletonMap("hot", phase));
            final PutLifecyclePolicyRequest lifecyclePolicyRequest = new PutLifecyclePolicyRequest(policy);
            client.indexLifecycle().putLifecyclePolicy(lifecyclePolicyRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            logger.error("error trying to store index template", e);
        }

        // store index template
        try {
            final PutIndexTemplateRequest indexTemplateRequest = new PutIndexTemplateRequest("links_template");
            indexTemplateRequest.patterns(Collections.singletonList("links-*"));
            final Settings settings = Settings.builder()
                    .put("number_of_shards", 1)
                    .put("index.lifecycle.name", "link_policy")
                    .put("index.lifecycle.rollover_alias", "links")
                    .build();
            indexTemplateRequest.settings(settings);
            indexTemplateRequest.mapping(LINKS_MAPPING, XContentType.JSON);

            final AcknowledgedResponse acknowledgedResponse = client.indices().putTemplate(indexTemplateRequest, RequestOptions.DEFAULT);
            if (!acknowledgedResponse.isAcknowledged()) {
                logger.error("storing index template was not acknowledged, index template may be missing!");
            }
        } catch (Exception e) {
            logger.error("error trying to store index template", e);
        }

        // bootstrap time series data
        boolean timeSeriesIndicesExist = true;
        try {
            final GetIndexRequest getIndexRequest = new GetIndexRequest("links-*");
            timeSeriesIndicesExist = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            logger.error("error trying retrieve index data", e);
        }

        boolean indexSampleDocuments = false;
        if (timeSeriesIndicesExist) {
            final CountResponse countResponse;
            try {
                countResponse = client.count(new CountRequest("links-*"), RequestOptions.DEFAULT);
                indexSampleDocuments = countResponse.getCount() == 0;
            } catch (Exception e) {
                logger.error("error counting document in [links-*]", e);
            }
        } else {
            try {
                final CreateIndexRequest createIndexRequest = new CreateIndexRequest("links-000001");
                createIndexRequest.alias(new Alias("links").writeIndex(true));
                client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            } catch (Exception e) {
                logger.error("error trying retrieve index data", e);
            }
            indexSampleDocuments = true;
        }

        // index sample documents to have a few test docs
        if (indexSampleDocuments) {
            try {
                // bootstrap some links into an empty system
                elasticsearchOperations.save(links());
            } catch (Exception e) {
                logger.error("error trying index sample data", e);
            }
        }

        // check if user index exists
        boolean userIndexExists = false;
        try {
            final GetIndexRequest getIndexRequest = new GetIndexRequest("users");
            userIndexExists = client.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            logger.error("error trying retrieve index data", e);
        }

        // create user index if needed with proper mapping
        if (!userIndexExists) {
            try {
                final CreateIndexRequest createIndexRequest = new CreateIndexRequest("users");
                createIndexRequest.mapping(USER_MAPPING, XContentType.JSON);
                client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
            } catch (Exception e) {
                logger.error("error trying retrieve index data", e);
            }
        }
        logger.info("Initial Elasticsearch writes done");
    }

    // a static list of links that gets initially added to an empty links index
    private static final List<Link> links() {
        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return List.of(
                new Link("Elasticsearch - Securing a search engine while maintaining usability", "Security is often an afterthought when writing applications. Time pressure to finish features or developers not being aware of issues can be two out of many reasons. This talk will use the Elasticsearch codebase as an example of how to write a broadly used software, but keep security in mind. Not only pure Java features like the Java Security Manager will be covered or how to write a secure scripting engine, but also operating system features that can be leveraged. The goal of this talk is most importantly to make you think about your own codebase and where you can invest time to improve security of it - with maybe less efforts than you would think.",
                        "https://spinscale.de/posts/2020-04-07-elasticsearch-securing-a-search-engine-while-maintaining-usability.html",
                        "elasticsearch", Date.from(LocalDate.of(2020, 4, 7).atStartOfDay().toInstant(ZoneOffset.UTC)), 12L, true, "spinscale")

                , new Link("Testing & releasing the Elastic stack", "Elasticsearch is well known piece of software. This talk explains the different levels of testing along with packaging and releasing as part of the Elastic Stack. Testing a well known software like Elasticsearch is not too different to any other software. In this session we will peak into the different testing strategies for unit and integration tests including randomized testing, how we leverage gradle, how we do packaging tests, how we test the REST layer, what our CI infrastructure and tooling around that looks like and finally what happens in order to release Elasticsearch and other parts of the Elastic Stack. ",
                        "https://spinscale.de/posts/2020-04-22-testing-and-releasing-elasticsearch-and-the-elastic-stack.html",
                        "elasticsearch", Date.from(LocalDate.of(2020, 4, 22).atStartOfDay().toInstant(ZoneOffset.UTC)), 2L, true, "spinscale")

                , new Link("Introduction into the Java HTTP REST client for Elasticsearch", "Elasticsearch comes with a bunch of clients for different languages, like JavaScript, Ruby, Go, .NET, PHP, Perl, Python and most recently even Rust. A late starter (starting in 5.x, but only fully supported from 7.0) was the Java High Level REST client, that intended to replace the TransportClient. This presentation talks a little bit about the reasoning while showing a lot of examples how to use the client, and even comes with a small sample project.",
                        "https://spinscale.de/posts/2020-04-15-introduction-into-the-elasticsearch-java-rest-client.html",
                        "elasticsearch", Date.from(LocalDate.of(2020, 4, 15).atStartOfDay().toInstant(ZoneOffset.UTC)), 5L, true, "spinscale")

                , new Link("The journey to support nanosecond timestamps in Elasticsearch", "The ability to store dates in nanosecond resolution required a significant refactoring within the Elasticsearch code base. Read this blog post for the why and how on our journey to be able to store dates in nanosecond resolution from Elasticsearch 7.0 onwards.",
                        "https://www.elastic.co/blog/journey-support-nanosecond-timestamps-elasticsearch",
                        "elasticsearch", Date.from(LocalDate.of(2019, 6, 27).atStartOfDay().toInstant(ZoneOffset.UTC)), 15L, true, "spinscale")

                , new Link("Elasticsearch Langdetect Ingest Processor", "Ingest processor doing language detection for fields. Uses the langdetect plugin to try to find out the language used in a field.",
                        "https://github.com/spinscale/elasticsearch-ingest-langdetect",
                        "elasticsearch", Date.from(LocalDate.of(2016, 6, 10).atStartOfDay().toInstant(ZoneOffset.UTC)), 42L, true, "spinscale")

                , new Link("Elasticsearch OpenNLP Ingest Processor", "An Elasticsearch ingest processor to do named entity extraction using Apache OpenNLP",
                        "https://github.com/spinscale/elasticsearch-ingest-opennlp",
                        "elasticsearch", Date.from(LocalDate.of(2016, 4, 25).atStartOfDay().toInstant(ZoneOffset.UTC)), 208L, true, "spinscale")

                , new Link("A cookiecutter template for an elasticsearch ingest processor plugin", "A cookiecutter template for an Elasticsearch Ingest Processor. This should simplify the creation of Elasticsearch Ingest Processors, this template will set up all the different java classes to get started.",
                        "https://github.com/spinscale/cookiecutter-elasticsearch-ingest-processor",
                        "elasticsearch", Date.from(LocalDate.of(2016, 4, 29).atStartOfDay().toInstant(ZoneOffset.UTC)), 37L, true, "spinscale")

                , new Link("Using the Elastic Stack to visualize the meetup.com reservation stream", "This repository contains a couple of configuration files to monitor the public meetup.com reservation stream via filebeat and index data directly into the Elasticsearch, and also automatically installs a nice to watch dashboard.",
                        "https://github.com/spinscale/elastic-stack-meetup-stream",
                        "elasticsearch", Date.from(LocalDate.of(2019, 8, 28).atStartOfDay().toInstant(ZoneOffset.UTC)), 1L, true, "spinscale")

                , new Link("Elasticsearch documentation alfred workflow\n", "An alfred workflow to easily search the elastic documentation",
                        "https://github.com/spinscale/alfred-workflow-elastic-docs",
                        "elastic", Date.from(LocalDate.of(2016, 7, 16).atStartOfDay().toInstant(ZoneOffset.UTC)), 10L, true, "spinscale")

                , new Link("Slow Query Logging for Elasticsearch and Elastic Cloud", "How do you log slow queries in Elasticsearch and especially on Elastic Cloud?",
                        "https://xeraa.net/blog/2020_slow-query-logging-elasticsearch-elastic-cloud/",
                        "cloud", Date.from(LocalDate.of(2020, 3, 22).atStartOfDay().toInstant(ZoneOffset.UTC)), 15L, true, "xeraa")

                , new Link("Custom Domains and Anonymous Access on Elastic Cloud", "Two common requests for Elastic Cloud are on the one hand: Custom domain names: Rather than using https://<UUID>.<region>.<cloud-provider>.cloud.es.io:9243 you might want to access Kibana or Elasticsearch on https://mydomain.com. and on the other hand anonymous Kibana access: Read-only access to dashboards or canvas for simple sharing without needing to log in.",
                        "https://xeraa.net/blog/2020_custom-domains-and-anonymous-access-on-elastic-cloud/",
                        "cloud", Date.from(LocalDate.of(2020, 4, 15).atStartOfDay().toInstant(ZoneOffset.UTC)), 22L, true, "spinscale")
        );
    }
}