package com.example.characterchat.rag.application;

import java.util.List;
import java.util.StringJoiner;

final class VectorLiteral {
	private VectorLiteral() { }
	static String format(List<Float> values) {
		StringJoiner joiner = new StringJoiner(",", "[", "]");
		values.forEach(value -> joiner.add(String.valueOf(value)));
		return joiner.toString();
	}
}
