package com.agentpanel.system.service;

import com.agentpanel.common.BusinessException;
import com.agentpanel.system.dto.MenuDto;
import com.agentpanel.system.entity.SysMenu;
import com.agentpanel.system.repository.SysMenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuRepository menuRepository;

    public List<MenuDto> list() {
        return menuRepository.findByDeletedFalseOrderByOrderNoAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public MenuDto create(MenuDto dto) {
        SysMenu menu = fromDto(new SysMenu(), dto);
        return toDto(menuRepository.save(menu));
    }

    @Transactional
    public MenuDto update(Long id, MenuDto dto) {
        SysMenu menu = menuRepository.findById(id).filter(m -> !m.isDeleted())
                .orElseThrow(() -> new BusinessException("菜单不存在"));
        return toDto(menuRepository.save(fromDto(menu, dto)));
    }

    @Transactional
    public void delete(Long id) {
        SysMenu menu = menuRepository.findById(id).filter(m -> !m.isDeleted())
                .orElseThrow(() -> new BusinessException("菜单不存在"));
        menu.setDeleted(true);
        menuRepository.save(menu);
    }

    private SysMenu fromDto(SysMenu menu, MenuDto dto) {
        menu.setName(dto.getName());
        menu.setPath(dto.getPath());
        menu.setIcon(dto.getIcon());
        menu.setComponent(dto.getComponent());
        menu.setParentId(dto.getParentId());
        menu.setOrderNo(dto.getOrderNo());
        menu.setPermissionId(dto.getPermissionId());
        menu.setHidden(dto.isHidden());
        return menu;
    }

    private MenuDto toDto(SysMenu menu) {
        MenuDto dto = new MenuDto();
        dto.setId(menu.getId());
        dto.setName(menu.getName());
        dto.setPath(menu.getPath());
        dto.setIcon(menu.getIcon());
        dto.setComponent(menu.getComponent());
        dto.setParentId(menu.getParentId());
        dto.setOrderNo(menu.getOrderNo());
        dto.setPermissionId(menu.getPermissionId());
        dto.setHidden(menu.isHidden());
        return dto;
    }
}
