package kz.genvibe.media_management.service.internal;

import kz.genvibe.media_management.model.domain.dto.jingle.JingleCreateDto;
import kz.genvibe.media_management.model.entity.AppUser;
import kz.genvibe.media_management.model.entity.Jingle;

import java.util.List;

public interface JingleService {
    int MONTHLY_LIMIT = 20;

    // Read methods
    List<Jingle> getJingleHistory(AppUser appUser);
    long getJinglesCreatedThisMonth(AppUser appUser);
    List<Jingle> getJingleRequestsToPause(AppUser appUser);

    // Modify methods
    void createJingle(AppUser appUser, JingleCreateDto dto);
    void deleteJingleById(long id, AppUser appUser);
    void setPauseApprovalStatus(long id, AppUser appUser);
    void addJingleToStores(long id, List<Long> idList, AppUser appUser);
    void requestToPauseJingle(long id);
}
