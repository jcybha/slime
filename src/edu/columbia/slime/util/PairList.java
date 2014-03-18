package edu.columbia.slime.util;

import java.util.List;
import java.util.ArrayList;

public class PairList<L, R> {
	private final List<L> left = new ArrayList<L>();
	private final List<R> right = new ArrayList<R>();

	public boolean add(L l, R r) {
		if (l == null || r == null)
			throw new RuntimeException("value is null (l=" + l + " r=" + r + ")");
		left.add(l);
		right.add(r);

		return true;
	}

	public L getLeft() {
		return left.get(0);
	}

	public R getRight() {
		return right.get(0);
	}

	public L getLeft(int index) {
		return left.get(index);
	}

	public R getRight(int index) {
		return right.get(index);
	}

	public boolean remove(int index) {
		left.remove(index);
		right.remove(index);

		return true;
	}

	public boolean isEmpty() {
		return left.isEmpty();
	}

	public boolean remove() {
		remove(0);

		return true;
	}
}
