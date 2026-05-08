package com.allcenter.modulelocation.service;

import com.allcenter.modulelocation.dto.SucursalDtos.CreateSucursalRequest;
import com.allcenter.modulelocation.dto.SucursalDtos.SucursalDto;
import com.allcenter.modulelocation.model.Sucursal;
import com.allcenter.modulelocation.repository.SucursalRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursalRepository;

    public List<SucursalDto> getBranches() {
        return sucursalRepository.findAll().stream()
                .map(
                        s ->
                                new SucursalDto(
                                        s.getId(),
                                        s.getNombre(),
                                        s.getDireccion(),
                                        s.getCiudad(),
                                        s.getDepartamento()))
                .toList();
    }

    @Transactional
    public SucursalDto createBranch(CreateSucursalRequest req) {
        Sucursal s = new Sucursal();
        s.setNombre(req.nombre().trim());
        s.setDireccion(req.direccion());
        s.setCiudad(req.ciudad());
        s.setDepartamento(req.departamento());
        s = sucursalRepository.save(s);
        return new SucursalDto(s.getId(), s.getNombre(), s.getDireccion(), s.getCiudad(), s.getDepartamento());
    }
}
