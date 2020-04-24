package de.spinscale.linkrating.entity;

import org.elasticsearch.index.VersionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

@Document(indexName = "users", shards = 1, versionType = VersionType.INTERNAL)
public class User {

    @Id
    private String id;

    @Field(type = FieldType.Keyword, name="user_id")
    private String userId;

    @Field(type = FieldType.Keyword, name="ids")
    private List<String> ids;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
