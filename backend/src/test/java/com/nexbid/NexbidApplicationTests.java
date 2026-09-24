package com.nexbid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.nexbid.support.PostgresTestcontainer;

/**
 * EN: Starts the whole application against a throwaway Postgres, so migrations and entities are proven together.
 * VI: Khởi động toàn bộ ứng dụng trên một Postgres dùng-một-lần, để migration và entity được kiểm chứng cùng nhau.
 */
@SpringBootTest
@Import(PostgresTestcontainer.class)
class NexbidApplicationTests {

	@Test
	void contextLoads() {
	}

}
