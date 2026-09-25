package kz.genvibe.media_management.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class UpdateEntity extends CreateEntity{

    @Column(nullable = false)
    @LastModifiedDate
    private Instant updatedAt;

    @Column(nullable = false)
    @LastModifiedBy
    private String updatedBy;

}
