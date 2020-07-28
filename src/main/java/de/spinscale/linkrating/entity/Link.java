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

package de.spinscale.linkrating.entity;

import org.apache.lucene.analysis.charfilter.HTMLStripCharFilter;
import org.elasticsearch.index.VersionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;

@Document(indexName = "links", shards = 1, versionType = VersionType.INTERNAL,  createIndex = false)
public class Link {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String category;

    private String title;

    private String description;

    @Field(type = FieldType.Keyword)
    private String url;

    @Field(name="submitted_by", type = FieldType.Keyword)
    private String submittedBy;

    @Field(name = "created_at", type=FieldType.Date, format = DateFormat.date_optional_time)
    private Date createdAt;

    @Field(type = FieldType.Boolean)
    private boolean approved;

    @Field(type = FieldType.Long)
    private Long votes;

    public Link() {}

    public Link(String title, String description, String url, String category,
                Date createdAt, Long votes, boolean approved, String submittedBy) {
        this.title = title;
        this.description = description;
        this.url = url;
        this.category = category;
        this.createdAt = createdAt;
        this.votes = votes;
        this.approved = approved;
        this.submittedBy = submittedBy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        if (category.length() > 100) {
            throw new IllegalArgumentException("category was more than 100 characters");
        }
        this.category = stripHTML(category.toLowerCase(Locale.ROOT));
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (title.length() > 100) {
            throw new IllegalArgumentException("title was more than 100 characters");
        }
        this.title = stripHTML(title);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url.length() > 500) {
            throw new IllegalArgumentException("url was more than 500 characters");
        }
        final String data = stripHTML(url);
        try {
            this.url = new URL(data).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("invalid url: " + url);
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description.length() > 1000) {
            throw new IllegalArgumentException("description was more than 1000 characters");
        }
        this.description = stripHTML(description);
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getAgo() {
        return ago(ZonedDateTime.now(ZoneOffset.UTC) ,createdAt.toInstant().atZone(ZoneOffset.UTC));
    }

    static String ago(ZonedDateTime now, ZonedDateTime input) {
        final Duration duration = Duration.between(input, now);
        final Period period = Period.between(input.toLocalDate(), now.toLocalDate());

        if (duration.isNegative()) {
            throw new IllegalArgumentException("expected input date [" + input + "] to be later than now [" + now + "]");
        }

        if (period.getYears() > 1) {
            return period.getYears() + " years ago";
        }
        if (period.getYears() == 1) {
            return "1 year ago";
        }
        if (period.getMonths() > 1) {
            return period.getMonths() + " months ago";
        }
        if (period.getMonths() == 1) {
            return "1 month ago";
        }
        if (period.getDays() > 1) {
            return period.getDays() + " days ago";
        }
        if (period.getDays() == 1) {
            return "yesterday";
        }
        if (duration.toHours() > 1) {
            return duration.toHours() + " hours ago";
        }
        if (duration.toHours() == 1) {
            return "1 hour ago";
        }
        if (duration.toMinutes() > 5) {
            return duration.toMinutes() + " minutes ago";
        }
        return "just now";
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVotes() {
        return votes;
    }

    public void setVotes(Long votes) {
        this.votes = votes;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public boolean isApproved() {
        return approved;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        if (submittedBy.length() > 100) {
            throw new IllegalArgumentException("title was more than 100 characters");
        }
        this.submittedBy = stripHTML(submittedBy);
    }

    // ensure no HTML gets into Elasticsearch
    private static String stripHTML(String input) {
        StringBuilder builder = new StringBuilder();
        try (HTMLStripCharFilter filter = new HTMLStripCharFilter(new StringReader(input))) {
            int ch;
            while ((ch = filter.read()) != -1) {
                builder.append((char)ch);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return builder.toString();
    }
}
