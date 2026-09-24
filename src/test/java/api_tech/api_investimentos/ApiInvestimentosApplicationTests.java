package api_tech.api_investimentos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class ApiInvestimentosApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void shouldExposeMigratedUserTable() {
		var userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tb_user", Integer.class);

		assertNotNull(userCount);
	}

}
