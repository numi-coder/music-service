package kz.genvibe.media_management.model.entity;

import jakarta.persistence.*;
import kz.genvibe.media_management.model.entity.base.BaseEntity;
import kz.genvibe.media_management.model.enums.MusicAtmosphere;
import kz.genvibe.media_management.model.enums.MusicMood;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "musics")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Music extends BaseEntity {

    @Column(nullable = false)
    private String fileUrl;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(length = 128, nullable = false)
    private List<MusicMood> mood = new ArrayList<>();

    @Column
    @Enumerated(EnumType.STRING)
    private MusicAtmosphere atmosphere;

    @Column(nullable = false)
    private String iconLocation;

    public final String getMoodNames() {
        StringBuilder sb = new StringBuilder();
        for (MusicMood m : mood) {
            sb.append(m.getName());
            sb.append(" + ");
        }
        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

}
