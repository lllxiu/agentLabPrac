package org.example.backend_practice.repository;

import org.example.backend_practice.dto.MessageResponse;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageRepositoryTest {

    private JdbcTemplate jdbc;
    private MessageRepository repository;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        // 每个测试使用独立的内存数据库。
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:repo_" + UUID.randomUUID().toString().replace("-", "") +
                        ";MODE=MySQL;DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");

        jdbc = new JdbcTemplate(dataSource);

        // 执行项目自己的建表脚本。
        new ResourceDatabasePopulator(
                new ClassPathResource("schema.sql")
        ).execute(dataSource);

        repository = new MessageRepository(jdbc);
        transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource)
        );

        jdbc.update(
                "INSERT INTO practice_conversations (id, title) VALUES (?, ?)",
                1L, "测试会话"
        );
    }

    @AfterEach
    void tearDown() {
        if (jdbc != null) {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    void stoppedMessageCanBeReadBack() {
        repository.save(1L, "assistant", "生成到一半的回答", "stopped");

        List<MessageResponse> result = repository.findByConversationId(1L);

        assertEquals(1, result.size());

        MessageResponse message = result.getFirst();
        assertTrue(message.id() > 0);
        assertEquals(1L, message.conversationId());
        assertEquals("assistant", message.role());
        assertEquals("生成到一半的回答", message.content());
        assertEquals("stopped", message.status());
    }

    @Test
    void threeArgumentSaveUsesCompletedStatus() {
        repository.save(1L, "user", "你好");

        MessageResponse message =
                repository.findByConversationId(1L).getFirst();

        assertEquals("completed", message.status());
    }

    @Test
    void queryReturnsOnlyThisConversationInIdOrder() {
        jdbc.update(
                "INSERT INTO practice_conversations (id, title) VALUES (?, ?)",
                2L, "另一个会话"
        );

        repository.save(1L, "user", "第一条");
        repository.save(2L, "user", "其他会话的消息");
        repository.save(1L, "assistant", "第二条");

        List<MessageResponse> result = repository.findByConversationId(1L);

        assertEquals(
                List.of("第一条", "第二条"),
                result.stream().map(MessageResponse::content).toList()
        );
        assertTrue(result.get(0).id() < result.get(1).id());
    }

    @Test
    void secondInsertFailureRollsBackFirstInsert() {
        assertThrows(DataIntegrityViolationException.class, () -> {
            transactions.executeWithoutResult(tx -> {
                repository.save(1L, "user", "这条也应该回滚", "completed");

                // content 是 NOT NULL，故意让第二次写入失败。
                repository.save(1L, "assistant", null, "completed");
            });
        });

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM practice_messages WHERE conversation_id = ?",
                Long.class,
                1L
        );

        assertEquals(0L, count.longValue());
    }
}

