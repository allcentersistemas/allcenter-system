package com.allcenter.modulelocation.service;

import com.allcenter.modulelocation.dto.UbicacionDtos.CreateUbicacionRequest;
import com.allcenter.modulelocation.dto.UbicacionDtos.UbicacionDto;
import com.allcenter.modulelocation.model.Ubicacion;
import com.allcenter.modulelocation.repository.UbicacionRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UbicacionService {

    private final UbicacionRepository ubicacionRepository;

    public List<UbicacionDto> getLocations() {
        return ubicacionRepository.findAll().stream()
                .map(
                        u ->
                                new UbicacionDto(
                                        u.getId(),
                                        u.getNombre(),
                                        u.getDireccion(),
                                        u.getDistrito(),
                                        u.getDepartamento(),
                                        u.getCiudad()))
                .toList();
    }

    @Transactional
    public UbicacionDto createLocation(CreateUbicacionRequest req) {
        Ubicacion u = new Ubicacion();
        u.setNombre(req.nombre().trim());
        u.setDireccion(req.direccion());
        u.setDistrito(req.distrito());
        u.setDepartamento(req.departamento());
        u.setCiudad(req.ciudad());
        u = ubicacionRepository.save(u);
        return new UbicacionDto(
                u.getId(), u.getNombre(), u.getDireccion(), u.getDistrito(), u.getDepartamento(), u.getCiudad());
    }
}
