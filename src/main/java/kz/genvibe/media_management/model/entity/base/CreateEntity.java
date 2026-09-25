package kz.genvibe.media_management.model.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.Instant;

@MappedSuperclass
@Getter
@Setter
public abstract class CreateEntity extends BaseEntity {

    @Column(updatable = false, nullable = false)
    @CreatedDate
    private Instant createdAt;

    @Column(updatable = false, nullable = false)
    @CreatedBy
    private String createdBy;

}
