package me.ifmo.backend.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import me.ifmo.backend.DTO.CoordinatesDTO;
import me.ifmo.backend.DTO.LocationDTO;
import me.ifmo.backend.DTO.RouteDTO;
import me.ifmo.backend.entities.*;
import me.ifmo.backend.repositories.ImportOperationRepository;
import me.ifmo.backend.repositories.RouteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImportTransactionService {

    private final ImportOperationRepository importOperationRepository;
    private final RouteRepository routeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportOperation createOperation(String username, String objectType) {
        ImportOperation op = new ImportOperation();
        op.setUsername(username);
        op.setObjectType(objectType);
        op.setStatus(ImportStatus.IN_PROGRESS);
        op.setStartedAt(LocalDateTime.now());
        return importOperationRepository.save(op);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(Long opId, int count) {
        ImportOperation op = importOperationRepository.findById(opId)
                .orElseThrow(() -> new EntityNotFoundException("Import operation not found: " + opId));
        op.setStatus(ImportStatus.SUCCESS);
        op.setImportedCount(count);
        op.setFinishedAt(LocalDateTime.now());
        importOperationRepository.save(op);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long opId, String error) {
        ImportOperation op = importOperationRepository.findById(opId)
                .orElseThrow(() -> new EntityNotFoundException("Import operation not found: " + opId));
        op.setStatus(ImportStatus.FAILED);
        op.setErrorMessage(error);
        op.setFinishedAt(LocalDateTime.now());
        importOperationRepository.save(op);
    }

    @Transactional
    public int importRoutes(MultipartFile file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        List<RouteDTO> dtoList = mapper.readValue(
                file.getInputStream(),
                new TypeReference<>() {
                }
        );

        if (dtoList.isEmpty()) {
            throw new IllegalArgumentException("Import file is empty");
        }

        List<Route> routes = new ArrayList<>();

        int index = 0;
        for (RouteDTO dto : dtoList) {
            index++;

            validateForCreate(dto, index);

            Route route = new Route();
            route.setName(dto.getName());
            route.setDistance(dto.getDistance());
            route.setRating(dto.getRating());
            route.setCreationDate(LocalDateTime.now());

            route.setCoordinates(toCoordinates(dto.getCoordinates(), index));
            route.setFrom(toLocation(dto.getFrom(), "from", index));
            route.setTo(toLocation(dto.getTo(), "to", index));

            routes.add(route);
        }

        routeRepository.saveAll(routes);
        return routes.size();
    }


    private void validateForCreate(RouteDTO dto, int index) {
        if (dto == null) {
            throw new IllegalArgumentException("Route at index " + index + " is null");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Route.name must not be blank at index " + index);
        }
        if (dto.getDistance() == null || dto.getDistance() <= 1.0f) {
            throw new IllegalArgumentException("Route.distance must be > 1 at index " + index);
        }
        if (dto.getRating() == null || dto.getRating() <= 0.0) {
            throw new IllegalArgumentException("Route.rating must be > 0 at index " + index);
        }
        if (dto.getCoordinates() == null) {
            throw new IllegalArgumentException("Route.coordinates must not be null at index " + index);
        }
        if (dto.getFrom() == null) {
            throw new IllegalArgumentException("Route.from must not be null at index " + index);
        }
        if (dto.getTo() == null) {
            throw new IllegalArgumentException("Route.to must not be null at index " + index);
        }
    }

    private Coordinates toCoordinates(CoordinatesDTO dto, int index) {
        if (dto.getX() == null) {
            throw new IllegalArgumentException("Coordinates.x must not be null at index " + index);
        }
        if (dto.getY() == null || dto.getY() <= -976.0f) {
            throw new IllegalArgumentException("Coordinates.y must be > -976 at index " + index);
        }
        Coordinates c = new Coordinates();
        c.setX(dto.getX());
        c.setY(dto.getY());
        return c;
    }

    private Location toLocation(LocationDTO dto, String role, int index) {
        if (dto.getY() == null) {
            throw new IllegalArgumentException("Location(" + role + ").y must not be null at index " + index);
        }
        if (dto.getZ() == null) {
            throw new IllegalArgumentException("Location(" + role + ").z must not be null at index " + index);
        }
        Location loc = new Location();
        loc.setX(dto.getX());
        loc.setY(dto.getY());
        loc.setZ(dto.getZ());
        return loc;
    }
}
