package com.leeburke.springgame.shared;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * Factory for the project's strict Jackson configuration, used for authored content and persisted
 * documents. Unknown, missing or null fields, unknown enum values, string/number coercion,
 * fractional integers, duplicate keys and trailing content all fail.
 * <p>
 * Each call builds a new, independently owned mapper; there is no shared instance.
 */
public final class StrictJson {

	private StrictJson() {
	}

	public static JsonMapper createMapper() {
		return JsonMapper.builder()
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
				.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
				.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
				.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
				.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
				.disable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
				.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
				// Text values must be JSON strings, not numbers or booleans.
				.withCoercionConfig(LogicalType.Textual, config -> config
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
				.build();
	}
}
