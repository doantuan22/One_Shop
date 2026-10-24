package com.oneshop.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Small operational journal, not a catalog entity or a background deletion worker. */
@Repository
public class ImageCleanupRepository {
    public record Task(UUID id, String publicId) { }
    private final JdbcTemplate jdbc;

    public ImageCleanupRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID enqueue(String publicId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into dbo.image_cleanup_tasks(task_id,public_id) values(?,?)", id.toString(), publicId);
        return id;
    }

    public Task lock(UUID id) {
        return jdbc.query("select task_id,public_id from dbo.image_cleanup_tasks with (UPDLOCK,ROWLOCK) where task_id=?",
                (rs, n) -> new Task(UUID.fromString(rs.getString(1)), rs.getString(2)), id.toString())
                .stream().findFirst().orElse(null);
    }

    /** HOLDLOCK protects the absence of references until the external delete finishes, including shared files. */
    public boolean isReferenced(String publicId) {
        for (String query : List.of(
                "select count_big(*) from dbo.brands with (UPDLOCK,HOLDLOCK) where logo_public_id=?",
                "select count_big(*) from dbo.categories with (UPDLOCK,HOLDLOCK) where image_public_id=?",
                "select count_big(*) from dbo.product_images with (UPDLOCK,HOLDLOCK) where public_id=?")) {
            Long count = jdbc.queryForObject(query, Long.class, publicId);
            if (count != null && count > 0) return true;
        }
        return false;
    }

    public void complete(UUID id) { jdbc.update("delete from dbo.image_cleanup_tasks where task_id=?", id.toString()); }

    public void failed(UUID id, String reason) {
        jdbc.update("update dbo.image_cleanup_tasks set attempts=attempts+1,last_error=? where task_id=?",
                reason.length() > 500 ? reason.substring(0, 500) : reason, id.toString());
    }

    public List<UUID> pending(int limit) {
        return jdbc.query("select top (?) task_id from dbo.image_cleanup_tasks order by created_at,task_id",
                (rs, n) -> UUID.fromString(rs.getString(1)), limit);
    }
}
