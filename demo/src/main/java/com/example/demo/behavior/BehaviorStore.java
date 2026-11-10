package com.example.demo.behavior;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BehaviorStore {
    private final JdbcTemplate jdbc;
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    public boolean insert(Long owner, BehaviorRequest event, String normalized, Instant now) {
        return jdbc.update("INSERT INTO user_behavior_events(id,customer_id,event_type,product_id,search_query,normalized_query,client_event_id,source_context,occurred_at) "
            + "SELECT ?,?,?,?,?,?,?,?,? WHERE EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.name='USER') AND NOT EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.name='ADMIN') ON CONFLICT (customer_id,client_event_id) DO NOTHING",
            UUID.randomUUID(), owner, event.eventType().name(), event.productId(), event.searchQuery(), normalized,
            event.clientEventId(), event.sourceContext() == null ? null : event.sourceContext().name(), ts(now), owner, owner) == 1;
    }
    private final org.springframework.jdbc.core.RowMapper<BehaviorEvent> mapper = (rs, row) -> new BehaviorEvent(
        rs.getObject("id", UUID.class), BehaviorType.valueOf(rs.getString("event_type")), rs.getObject("product_id", UUID.class),
        rs.getString("search_query"), rs.getObject("client_event_id", UUID.class),
        rs.getString("source_context") == null ? null : SourceContext.valueOf(rs.getString("source_context")), rs.getTimestamp("occurred_at").toInstant());
    public Optional<BehaviorEvent> retry(Long owner, UUID clientId) {
        return jdbc.query("SELECT * FROM user_behavior_events WHERE customer_id=? AND client_event_id=?", mapper, owner, clientId).stream().findFirst();
    }
    public BehaviorPage history(Long owner, BehaviorType type, Instant from, Instant to, int page, int size) {
        String where = " FROM user_behavior_events WHERE customer_id=? AND occurred_at>=? AND occurred_at<=?";
        List<Object> args = new ArrayList<>(List.of(owner, ts(from), ts(to)));
        if (type != null) { where += " AND event_type=?"; args.add(type.name()); }
        long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(size); args.add((long)page * size);
        var items = jdbc.query("SELECT *" + where + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?", mapper, args.toArray());
        return new BehaviorPage(items, page, size, total, (total + size - 1) / size);
    }
    public BehaviorOverview overview(Instant from, Instant to, int days) {
        var counts = new LinkedHashMap<String, Long>();
        for (var type : BehaviorType.values()) counts.put(type.name(), 0L);
        jdbc.query("SELECT event_type,count(*) AS n FROM user_behavior_events WHERE occurred_at>=? AND occurred_at<=? GROUP BY event_type",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> counts.put(rs.getString(1), rs.getLong(2)), ts(from), ts(to));
        return new BehaviorOverview(days, from, to, counts,
            topProducts("PRODUCT_VIEW", from, to), topProducts("PRODUCT_CLICK", from, to),
            jdbc.query("SELECT normalized_query AS query,count(*) AS count FROM user_behavior_events "
                + "WHERE occurred_at>=? AND occurred_at<=? AND event_type='SEARCH' GROUP BY normalized_query "
                + "HAVING count(DISTINCT customer_id)>=3 ORDER BY count(*) DESC,normalized_query ASC LIMIT 10",
                (rs, row) -> new BehaviorOverview.SearchCount(rs.getString(1), rs.getLong(2)), ts(from), ts(to)));
    }
    private List<BehaviorOverview.ProductCount> topProducts(String type, Instant from, Instant to) {
        return jdbc.query("SELECT product_id,count(*) AS count FROM user_behavior_events WHERE occurred_at>=? AND occurred_at<=? "
            + "AND event_type=? GROUP BY product_id ORDER BY count(*) DESC,product_id ASC LIMIT 10", (rs, row) -> new BehaviorOverview.ProductCount(rs.getObject(1, UUID.class), rs.getLong(2)), ts(from), ts(to), type);
    }
    public int cleanup(Instant cutoff, int batchSize) {
        return jdbc.update("DELETE FROM user_behavior_events WHERE id IN (SELECT id FROM user_behavior_events WHERE occurred_at<? "
            + "ORDER BY occurred_at,id LIMIT ? FOR UPDATE SKIP LOCKED)", ts(cutoff), batchSize);
    }
}
