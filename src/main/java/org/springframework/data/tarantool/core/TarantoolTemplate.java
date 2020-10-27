package org.springframework.data.tarantool.core;

import io.tarantool.driver.api.TarantoolClient;
import io.tarantool.driver.api.TarantoolResult;
import io.tarantool.driver.api.conditions.Conditions;
import io.tarantool.driver.api.tuple.TarantoolTuple;
import io.tarantool.driver.api.tuple.TarantoolTupleImpl;
import io.tarantool.driver.mappers.TarantoolCallResultMapper;
import io.tarantool.driver.mappers.TarantoolCallResultMapperFactory;
import io.tarantool.driver.metadata.TarantoolSpaceMetadata;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.data.tarantool.core.convert.TarantoolConverter;
import org.springframework.data.tarantool.core.mapping.TarantoolMappingContext;
import org.springframework.data.tarantool.core.mapping.TarantoolPersistentEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TarantoolTemplate implements TarantoolOperations {

    private final TarantoolClient tarantoolClient;
    private final TarantoolMappingContext mappingContext;
    private final TarantoolConverter converter;
    private final TarantoolExceptionTranslator exceptionTranslator;
    private final TarantoolCallResultMapperFactory tarantoolResultMapperFactory;

    public TarantoolTemplate(TarantoolClient tarantoolClient,
                             TarantoolMappingContext mappingContext,
                             TarantoolConverter converter) {
        this.tarantoolClient = tarantoolClient;
        this.mappingContext = mappingContext;
        this.converter = converter;
        this.exceptionTranslator = new DefaultTarantoolExceptionTranslator();
        this.tarantoolResultMapperFactory =
                new TarantoolCallResultMapperFactory(tarantoolClient.getConfig().getMessagePackMapper());
    }

    @Override
    public <T> T findOne(Conditions query, Class<T> entityClass) {
        Assert.notNull(query, "Query must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.space(entity.getSpaceName()).select(query)
        );
        return mapFirstToEntity(result, entityClass);
    }

    @Override
    public <T> List<T> find(Conditions query, Class<T> entityClass) {
        Assert.notNull(query, "Query must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.space(entity.getSpaceName()).select(query)
        );
        return result.stream().map(t -> mapToEntity(t, entityClass)).collect(Collectors.toList());
    }

    @Override
    public <T, ID> T findById(ID id, Class<T> entityClass) {
        Assert.notNull(id, "Id must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() -> {
            Conditions query = idQueryFromEntity(id).withLimit(1);
            return tarantoolClient.space(entity.getSpaceName()).select(query);
        });
        return mapFirstToEntity(result, entityClass);
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.space(entity.getSpaceName()).select(Conditions.any())
        );
        return result.stream().map(t -> mapToEntity(t, entityClass)).collect(Collectors.toList());
    }

    @Override
    public <T> List<T> findAndRemove(Conditions query, Class<T> entityType) {
        List<T> entities = find(query, entityType);
        return entities.stream().map(e -> remove(e, entityType)).collect(Collectors.toList());
    }

    @Override
    public <T> Long count(Conditions query, Class<T> entityType) {
        // not supported in the driver yet. TODO change this when implemented in the driver
        throw new NotImplementedException();
    }

    @Override
    public <T> T insert(T entity, Class<T> entityClass) {
        Assert.notNull(entity, "Entity must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolPersistentEntity<?> entityMetadata = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.space(entityMetadata.getSpaceName()).insert(mapToTuple(entity, entityMetadata))
        );
        return mapFirstToEntity(result, entityClass);
    }

    @Override
    public <T> T save(T entity, Class<T> entityClass) {
        Assert.notNull(entity, "Entity must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolPersistentEntity<?> entityMetadata = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.space(entityMetadata.getSpaceName()).replace(mapToTuple(entity, entityMetadata))
        );
        return mapFirstToEntity(result, entityClass);
    }

    @Override
    public <T> T remove(T entity, Class<T> entityClass) {
        Assert.notNull(entity, "Entity must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        Conditions query = idQueryFromEntity(entity);
        return removeInternal(query, entityClass);
    }

    @Override
    public <T, ID> T removeById(ID id, Class<T> entityClass) {
        Assert.notNull(id, "ID must not be null!");
        Assert.notNull(entityClass, "Type must not be null!");

        Conditions query = idQueryFromEntity(id);
        return removeInternal(query, entityClass);
    }

    @Override
    public <T> T call(String functionName, Object[] parameters, Class<T> entityType) {
        Assert.hasText(functionName, "Function name must not be null or empty!");
        Assert.notNull(entityType, "Type must not be null!");

        List<T> result = callForList(functionName, parameters, entityType);
        return result != null && result.size() > 0 ? result.get(0) : null;
    }

    @Override
    public <T> List<T> callForList(String functionName, Object[] parameters, Class<T> entityClass) {
        Assert.hasText(functionName, "Function name must not be null or empty!");
        Assert.notNull(entityClass, "Type must not be null!");

        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.call(
                functionName,
                Arrays.asList(parameters),
                tarantoolClient.getConfig().getMessagePackMapper(),
                getResultMapperForEntity(entityClass))
        );
        return result.stream().map(t -> mapToEntity(t, entityClass)).collect(Collectors.toList());
    }

    private <T> TarantoolCallResultMapper<TarantoolTuple> getResultMapperForEntity(Class<T> entityClass) {
        // TODO cache and lookup
        TarantoolPersistentEntity<?> entityMetadata = mappingContext.getRequiredPersistentEntity(entityClass);
        Optional<TarantoolSpaceMetadata> spaceMetadata = tarantoolClient.metadata()
                .getSpaceByName(entityMetadata.getSpaceName());
        return tarantoolResultMapperFactory.withDefaultTupleValueConverter(spaceMetadata.orElse(null));
    }

    @Nullable
    private <T> T removeInternal(Conditions query, Class<T> entityClass) {
        TarantoolPersistentEntity<?> entityMetadata = mappingContext.getRequiredPersistentEntity(entityClass);
        TarantoolResult<TarantoolTuple> result = executeSync(() ->
            tarantoolClient.space(entityMetadata.getSpaceName()).delete(query)
        );
        return mapFirstToEntity(result, entityClass);
    }

    private <T> Conditions idQueryFromEntity(T entity) {
        Conditions query = Conditions.any();
        getConverter().write(entity, query);
        return query;
    }

    private <T> T mapFirstToEntity(TarantoolResult<TarantoolTuple> tuples, Class<T> entityClass) {
        return mapToEntity(tuples.stream()
                    .findFirst()
                    .orElse(null),
                entityClass);
    }

    private <T> T mapToEntity(@Nullable TarantoolTuple tuple, Class<T> entityClass) {
        return getConverter().read(entityClass, tuple);
    }

    private <T> TarantoolTuple mapToTuple(T entity, TarantoolPersistentEntity<?> entityMetadata) {
        Optional<TarantoolSpaceMetadata> spaceMetadata = tarantoolClient.metadata()
                .getSpaceByName(entityMetadata.getSpaceName());
        TarantoolTuple tuple = spaceMetadata.isPresent() ?
                new TarantoolTupleImpl(tarantoolClient.getConfig().getMessagePackMapper(), spaceMetadata.get()) :
                new TarantoolTupleImpl(tarantoolClient.getConfig().getMessagePackMapper());
        getConverter().write(entity, tuple);
        return tuple;
    }

    @Override
    public TarantoolConverter getConverter() {
        return converter;
    }

    private <R> R executeSync(Supplier<CompletableFuture<R>> func) {
        try {
            return func.get().get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                DataAccessException wrapped = exceptionTranslator
                        .translateExceptionIfPossible((RuntimeException) e.getCause());
                if (wrapped != null) {
                    throw wrapped;
                }
            }
            throw new DataRetrievalFailureException(e.getMessage(), e.getCause());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
