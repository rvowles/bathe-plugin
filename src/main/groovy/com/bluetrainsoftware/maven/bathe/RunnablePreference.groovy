package com.bluetrainsoftware.maven.bathe

/**
 * author: Richard Vowles - http://gplus.to/Richard.Vowles
 */
public enum RunnablePreference {
	/**
	 * once in the list, can't be overwritten. This means the current project takes precedence.
	 */
	first,
	/**
	 * overwritten with each detection
	 */
	last,
	/**
	 * clash causes failure
	 */
	fail
}