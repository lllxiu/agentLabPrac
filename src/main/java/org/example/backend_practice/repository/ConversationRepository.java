package org.example.backend_practice.repository;


import org.example.backend_practice.dto.ConversationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class ConversationRepository {
    private  final JdbcTemplate jdbcTemplate;
    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

    }

    public ConversationResponse create(String title) {
        String sql = "insert into practice_conversations(title) values(?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            statement.setString(1, title);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();

        if (id == null) {
            throw new IllegalStateException("创建会话未获取编号");

        }

        return new ConversationResponse(id.longValue(), title);
    }

    public List<ConversationResponse> list(){
        String sql="select id,title from practice_conversations order by id";

        return jdbcTemplate.query(sql,(rs, rowNum) ->
                new ConversationResponse(
                        rs.getLong("id"),
                        rs.getString("title")
                )
        );

    }

    public boolean exists(Long conversationId) {
        String sql="select count(*) from practice_conversations where id=?";

        Long count=jdbcTemplate.queryForObject(
                sql,
                Long.class,
                conversationId
        );

        return count!=null&&count>0;
    }
}


