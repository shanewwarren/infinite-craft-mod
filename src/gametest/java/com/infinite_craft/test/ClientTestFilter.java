package com.infinite_craft.test;

import java.util.Arrays;

/**
 * Fabric runs every client game test in one window. While working on one, skip the rest:
 * `./gradlew runClientGameTest -PclientTests=Vehicle` runs only tests whose class name contains Vehicle
 * (comma-separate several). Without it every test runs, as before a commit
 */
public class ClientTestFilter {

	public static boolean skip(Class<?> test) {
		String filter = System.getProperty("infiniteCraft.clientTests", "");
		if (filter.isBlank()) {
			return false;
		}
		boolean skip = Arrays.stream(filter.split(",")).map(String::strip).noneMatch(test.getSimpleName()::contains);
		if (skip) {
			System.out.printf("[ClientTestFilter] skipping %s (clientTests=%s)%n", test.getSimpleName(), filter);
		}
		return skip;
	}
}
