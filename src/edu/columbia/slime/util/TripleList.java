package edu.columbia.slime.util;

import java.util.List;
import java.util.ArrayList;

public class TripleList<L, M, R> {
	private final List<L> left = new ArrayList<L>();
	private final List<M> middle = new ArrayList<M>();
	private final List<R> right = new ArrayList<R>();

	public boolean add(L l, M m, R r) {
		// allowing r to be null
		if (l == null || m == null)
			throw new RuntimeException("l or m value is null (l=" + l + " m=" + m + " r=" + r + ")");
		left.add(l);
		middle.add(m);
		right.add(r);

		return true;
	}

	public L getLeft() {
		return left.get(0);
	}

	public M getMiddle() {
		return middle.get(0);
	}

	public R getRight() {
		return right.get(0);
	}

	public L getLeft(int index) {
		return left.get(index);
	}

	public M getMiddle(int index) {
		return middle.get(index);
	}

	public R getRight(int index) {
		return right.get(index);
	}

	public boolean remove(int index) {
		left.remove(index);
		middle.remove(index);
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
