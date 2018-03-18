package io.crnk.core.engine.internal.dispatcher.controller;

import io.crnk.core.engine.dispatcher.Response;
import io.crnk.core.engine.document.Document;
import io.crnk.core.engine.http.HttpMethod;
import io.crnk.core.engine.internal.dispatcher.path.JsonPath;
import io.crnk.core.engine.internal.dispatcher.path.ResourcePath;
import io.crnk.core.engine.internal.document.mapper.DocumentMapper;
import io.crnk.core.engine.internal.document.mapper.DocumentMappingConfig;
import io.crnk.core.engine.internal.repository.ResourceRepositoryAdapter;
import io.crnk.core.engine.query.QueryAdapter;
import io.crnk.core.engine.registry.RegistryEntry;
import io.crnk.core.engine.result.Result;
import io.crnk.core.exception.ResourceNotFoundException;
import io.crnk.core.repository.response.JsonApiResponse;
import io.crnk.core.utils.Nullable;
import io.crnk.legacy.internal.RepositoryMethodParameterProvider;

import java.io.Serializable;

public class CollectionGet extends ResourceIncludeField {

	/**
	 * Check if it is a GET request for a collection of resources.
	 */
	@Override
	public boolean isAcceptable(JsonPath jsonPath, String requestType) {
		return jsonPath.isCollection()
				&& jsonPath instanceof ResourcePath
				&& HttpMethod.GET.name().equals(requestType);
	}

	@Override
	public Result<Response> handleAsync(JsonPath jsonPath, QueryAdapter queryAdapter, RepositoryMethodParameterProvider
			parameterProvider, Document requestBody) {
		String resourceName = jsonPath.getElementName();
		RegistryEntry registryEntry = context.getResourceRegistry().getEntry(resourceName);
		if (registryEntry == null) {
			throw new ResourceNotFoundException(resourceName);
		}

		DocumentMappingConfig docummentMapperConfig = DocumentMappingConfig.create().setParameterProvider(parameterProvider);
		DocumentMapper documentMapper = context.getDocumentMapper();

		ResourceRepositoryAdapter resourceRepository = registryEntry.getResourceRepository(parameterProvider);
		Result<JsonApiResponse> response;
		if (jsonPath.getIds() == null || jsonPath.getIds().getIds().isEmpty()) {
			response = resourceRepository.findAll(queryAdapter);
		} else {
			Class<? extends Serializable> idType = (Class<? extends Serializable>) registryEntry
					.getResourceInformation().getIdField().getType();
			Iterable<? extends Serializable> parsedIds = context.getTypeParser().parse((Iterable<String>) jsonPath.getIds().getIds(),
					idType);
			response = resourceRepository.findAll(parsedIds, queryAdapter);
		}

		return response.merge(it -> documentMapper.toDocument(it, queryAdapter, docummentMapperConfig)).map(this::toResponse);
	}


	public Response toResponse(Document document) {
		// return explicit { data : null } if values found
		if (!document.getData().isPresent()) {
			document.setData(Nullable.nullValue());
		}
		return new Response(document, 200);
	}
}
