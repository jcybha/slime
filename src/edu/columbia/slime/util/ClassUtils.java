package edu.columbia.slime.util;

import java.util.Collection;


public class ClassUtils {
	private static Class<?> mainClass;

	public static Class<?> getMainClass() {
		if (mainClass != null)
			return mainClass;

		Collection<StackTraceElement[]> stacks = Thread.getAllStackTraces().values();
		for (StackTraceElement[] currStack : stacks) {
			if (currStack.length == 0)
				continue;
			StackTraceElement lastElem = currStack[currStack.length - 1];
			if (lastElem.getMethodName().equals("main")) {
				try {
					String mainClassName = lastElem.getClassName();
					mainClass = Class.forName(mainClassName);
					return mainClass;
				} catch (ClassNotFoundException e) {
					// bad class name in line containing main?! 
					// shouldn't happen
					e.printStackTrace();
				}
			}
		}
		/* probably the main thread has been finished */
		return null;
	}
}
