package org.springframework.data.jdbc.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactory;
import org.springframework.data.jdbc.testing.DatabaseType;
import org.springframework.data.jdbc.testing.EnabledOnDatabase;
import org.springframework.data.jdbc.testing.IntegrationTest;
import org.springframework.data.jdbc.testing.TestConfiguration;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Integration tests with collections chain.
 *
 * @author Yunyoung LEE
 * @author Nikita Konev
 * @author Sharang Gupta
 */
@IntegrationTest
@EnabledOnDatabase(DatabaseType.HSQL)
class JdbcRepositoryWithCollectionsChainHsqlIntegrationTests {

	@Autowired NamedParameterJdbcTemplate template;
	@Autowired DummyEntityRepository repository;
	@Autowired ListRootRepository listRootRepository;

	private static DummyEntity createDummyEntity() {
		return createDummyEntity("Entity Name");
	}

	private static DummyEntity createDummyEntity(String name) {

		DummyEntity entity = new DummyEntity();
		entity.name = name;
		return entity;
	}

	@Test // DATAJDBC-551, GH-2123
	void deleteByName() {

		ChildElement element1 = createChildElement("one");
		ChildElement element2 = createChildElement("two");

		DummyEntity entity = createDummyEntity();
		entity.content.add(element1);
		entity.content.add(element2);

		entity = repository.save(entity);

		assertThat(repository.deleteByName("Entity Name")).isEqualTo(1);

		assertThat(repository.findById(entity.id)).isEmpty();

		assertThat(countRows("child_element")).isEqualTo(0);
		assertThat(countRows("grand_child_element")).isEqualTo(0);
	}

	@Test
	void deleteByNameWithNestedListsWithoutIds() {

		ListGrandChild grandChild = new ListGrandChild();
		grandChild.name = "grand child";
		ListChild child = new ListChild();
		child.name = "child";
		child.children.add(grandChild);
		ListRoot root = new ListRoot();
		root.name = "list root";
		root.children.add(child);

		root = listRootRepository.save(root);

		assertThat(listRootRepository.deleteByName("list root")).isEqualTo(1);

		assertThat(listRootRepository.findById(root.id)).isEmpty();

		assertThat(countRows("list_child")).isEqualTo(0);
		assertThat(countRows("list_grand_child")).isEqualTo(0);
	}

	@Test // GH-2417
	void deleteByNameKeepsTheGrandChildrenOfOtherAggregates() {

		DummyEntity other = createDummyEntity("Other Name");
		other.content.add(createChildElement("three"));
		other.content.add(createChildElement("four"));
		other = repository.save(other);

		DummyEntity entity = createDummyEntity();
		entity.content.add(createChildElement("one"));
		entity.content.add(createChildElement("two"));
		entity = repository.save(entity);

		// the id of the deleted aggregate root is also the id of a child of the other aggregate
		assertThat(other.content).extracting(child -> child.id).contains(entity.id);

		assertThat(repository.deleteByName("Entity Name")).isEqualTo(1);

		DummyEntity reloaded = repository.findById(other.id).orElseThrow();
		assertThat(reloaded.content).hasSize(2).allSatisfy(child -> assertThat(child.content).hasSize(2));
	}

	@Test
	void deleteByNameKeepsReadOnlyChildren() {

		DummyEntity entity = repository.save(createDummyEntity());
		template.update("insert into read_only_element (dummy_entity, name) values (:id, 'read only')",
				Map.of("id", entity.id));

		assertThat(repository.deleteByName("Entity Name")).isEqualTo(1);

		assertThat(repository.findById(entity.id)).isEmpty();
		assertThat(countRows("read_only_element")).isEqualTo(1);
	}

	private long countRows(String table) {
		return template.queryForObject("select count(1) from " + table, new HashMap<>(), Long.class);
	}

	private ChildElement createChildElement(String name) {

		ChildElement element = new ChildElement();
		element.name = name;
		element.content.add(createGrandChildElement(name + "1"));
		element.content.add(createGrandChildElement(name + "2"));
		return element;
	}

	private GrandChildElement createGrandChildElement(String content) {

		GrandChildElement element = new GrandChildElement();
		element.content = content;
		return element;
	}

	interface DummyEntityRepository extends CrudRepository<DummyEntity, Long> {
		long deleteByName(String name);
	}

	interface ListRootRepository extends CrudRepository<ListRoot, Long> {
		long deleteByName(String name);
	}

	@Configuration
	@Import(TestConfiguration.class)
	static class Config {

		@Autowired JdbcRepositoryFactory factory;

		@Bean
		Class<?> testClass() {
			return JdbcRepositoryWithCollectionsChainHsqlIntegrationTests.class;
		}

		@Bean
		DummyEntityRepository dummyEntityRepository() {
			return factory.getRepository(DummyEntityRepository.class);
		}

		@Bean
		ListRootRepository listRootRepository() {
			return factory.getRepository(ListRootRepository.class);
		}
	}

	static class DummyEntity {

		String name;
		Set<ChildElement> content = new HashSet<>();
		@ReadOnlyProperty Set<ReadOnlyElement> readOnly = new HashSet<>();
		@Id private Long id;

	}

	static class ChildElement {

		String name;
		Set<GrandChildElement> content = new HashSet<>();
		@Id @Column("CHILD_ID") private Long id;
	}

	static class GrandChildElement {

		String content;
		@Id private Long id;
	}

	static class ReadOnlyElement {

		String name;
		@Id private Long id;
	}

	static class ListRoot {

		String name;
		List<ListChild> children = new ArrayList<>();
		@Id private Long id;
	}

	static class ListChild {

		String name;
		List<ListGrandChild> children = new ArrayList<>();
	}

	static class ListGrandChild {

		String name;
	}

}
