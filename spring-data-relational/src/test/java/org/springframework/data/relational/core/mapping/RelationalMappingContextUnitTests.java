/*
 * Copyright 2018-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.relational.core.mapping;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.relational.core.sql.SqlIdentifier;

/**
 * Unit tests for {@link RelationalMappingContext}.
 *
 * @author Toshiaki Maki
 * @author Jens Schauder
 */
public class RelationalMappingContextUnitTests {

	RelationalMappingContext context = new RelationalMappingContext();
	SimpleTypeHolder holder = new SimpleTypeHolder(new HashSet<>(List.of(UUID.class)), true);

	@BeforeEach
	void setup() {
		context.setSimpleTypeHolder(holder);
	}

	@Test // DATAJDBC-229
	public void uuidPropertyIsNotEntity() {

		RelationalPersistentEntity<?> entity = context.getPersistentEntity(EntityWithUuid.class);
		RelationalPersistentProperty uuidProperty = entity.getRequiredPersistentProperty("uuid");

		assertThat(uuidProperty.isEntity()).isFalse();
	}

	@Test // GH-1525
	public void canObtainAggregatePath() {

		PersistentPropertyPath<RelationalPersistentProperty> path = context.getPersistentPropertyPath("uuid",
				EntityWithUuid.class);
		AggregatePath aggregatePath = context.getAggregatePath(path);

		assertThat((Object) aggregatePath).isNotNull();
	}

	@Test // GH-1525
	public void innerAggregatePathsGetCached() {

		context = new RelationalMappingContext();
		context.setSimpleTypeHolder(holder);

		PersistentPropertyPath<RelationalPersistentProperty> path = context.getPersistentPropertyPath("uuid",
				EntityWithUuid.class);

		AggregatePath one = context.getAggregatePath(path);
		AggregatePath two = context.getAggregatePath(path);

		assertThat((Object) one).isSameAs(two);
	}

	@Test // GH-1525
	public void rootAggregatePathsGetCached() {

		context = new RelationalMappingContext();
		context.setSimpleTypeHolder(holder);

		AggregatePath one = context.getAggregatePath(context.getRequiredPersistentEntity(EntityWithUuid.class));
		AggregatePath two = context.getAggregatePath(context.getRequiredPersistentEntity(EntityWithUuid.class));

		assertThat((Object) one).isSameAs(two);
	}

	@Test // GH-1586
	void correctlyCascadesPrefix() {

		RelationalPersistentEntity<?> entity = context.getRequiredPersistentEntity(WithEmbedded.class);

		RelationalPersistentProperty parent = entity.getRequiredPersistentProperty("parent");
		RelationalPersistentEntity<?> parentEntity = context.getRequiredPersistentEntity(parent);
		RelationalPersistentProperty child = parentEntity.getRequiredPersistentProperty("child");
		RelationalPersistentEntity<?> childEntity = context.getRequiredPersistentEntity(child);
		RelationalPersistentProperty name = childEntity.getRequiredPersistentProperty("name");

		assertThat(parent.getEmbeddedPrefix()).isEqualTo("prnt_");
		assertThat(child.getEmbeddedPrefix()).isEqualTo("prnt_chld_");
		assertThat(name.getColumnName()).isEqualTo(SqlIdentifier.quoted("PRNT_CHLD_NAME"));
	}

	@Test // GH-1657
	void aggregatePathsOfBasePropertyForDifferentInheritedEntitiesAreDifferent() {

		PersistentPropertyPath<RelationalPersistentProperty> path1 = context.getPersistentPropertyPath("name",
				Inherit1.class);
		PersistentPropertyPath<RelationalPersistentProperty> path2 = context.getPersistentPropertyPath("name",
				Inherit2.class);

		AggregatePath aggregatePath1 = context.getAggregatePath(path1);
		AggregatePath aggregatePath2 = context.getAggregatePath(path2);

		assertThat((Object) aggregatePath1).isNotEqualTo(aggregatePath2);
	}

	@Test // GH-1952
	void acceptsReferencedSimpleProperty() {

		RelationalPersistentEntity<?> entity = context.getRequiredPersistentEntity(WithReferencedProperty.class);

		assertThat(entity.getRequiredPersistentProperty("children").getReferencedProperty())
				.isEqualTo(entity.getRequiredPersistentProperty("reference"));
	}

	@Test // GH-1952
	void rejectsUnknownReferencedProperty() {

		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> context.getRequiredPersistentEntity(WithUnknownReferencedProperty.class)) //
				.havingRootCause().withMessageContaining("unknown");
	}

	@Test // GH-1952
	void rejectsReferencedPropertyOfEntityWithoutId() {

		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> context.getRequiredPersistentEntity(WithReferencedPropertyButNoId.class)) //
				.havingRootCause().withMessageContaining("identifier");
	}

	@Test // GH-1952
	void rejectsReferencedPropertyWithinEmbeddable() {

		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> context.getRequiredPersistentEntity(WithEmbeddedReferencingProperty.class)) //
				.havingRootCause().withMessageContaining("identifier");
	}

	@Test // GH-1952
	void rejectsReferencedEntityProperty() {

		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> context.getRequiredPersistentEntity(WithReferencedEntityProperty.class)) //
				.havingRootCause().withMessageContaining("simple");
	}

	@Test // GH-1952
	void rejectsReferencedCollectionProperty() {

		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> context.getRequiredPersistentEntity(WithReferencedCollectionProperty.class)) //
				.havingRootCause().withMessageContaining("simple");
	}

	@Test // GH-1952
	void rejectsReferencedPropertyForSimpleValues() {

		assertThatExceptionOfType(MappingException.class)
				.isThrownBy(() -> context.getRequiredPersistentEntity(WithReferencedPropertyForSimpleValues.class)) //
				.havingRootCause().withMessageContaining("entities");
	}

	static class EntityWithUuid {
		@Id UUID uuid;
	}

	static class WithEmbedded {
		@Embedded.Empty(prefix = "prnt_") Parent parent;
	}

	static class WithEmbeddedId {
		@Embedded.Nullable
		@Id CompositeId id;
	}

	private record CompositeId(int a, int b) {
	}

	static class Parent {

		@Embedded.Empty(prefix = "chld_") Child child;
	}

	static class Child {
		String name;
	}

	static class Base {
		String name;
	}

	static class Inherit1 extends Base {}

	static class Inherit2 extends Base {}

	static class WithReferencedProperty {

		@Id Long id;
		String reference;
		@MappedCollection(referencedProperty = "reference") Set<Child> children;
	}

	static class WithUnknownReferencedProperty {

		@Id Long id;
		@MappedCollection(referencedProperty = "unknown") Set<Child> children;
	}

	static class WithReferencedPropertyButNoId {

		String reference;
		@MappedCollection(referencedProperty = "reference") Set<Child> children;
	}

	static class WithEmbeddedReferencingProperty {

		@Id Long id;
		@Embedded.Nullable WithReferencedPropertyButNoId embedded;
	}

	static class WithReferencedEntityProperty {

		@Id Long id;
		Child child;
		@MappedCollection(referencedProperty = "child") Set<Child> children;
	}

	static class WithReferencedCollectionProperty {

		@Id Long id;
		List<String> references;
		@MappedCollection(referencedProperty = "references") Set<Child> children;
	}

	static class WithReferencedPropertyForSimpleValues {

		@Id Long id;
		String reference;
		@MappedCollection(referencedProperty = "reference") List<String> values;
	}
}
