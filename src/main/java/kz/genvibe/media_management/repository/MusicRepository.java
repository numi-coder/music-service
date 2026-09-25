package kz.genvibe.media_management.repository;

import kz.genvibe.media_management.model.entity.Music;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MusicRepository extends JpaRepository<Music, Long> {
    List<Music> findDistinctBy();

    @Query(
        value = "SELECT * FROM musics WHERE atmosphere = :atmosphere AND mood && CAST(:moods AS varchar[]) LIMIT 1",
        nativeQuery = true
    )
    Optional<Music> findTopByAtmosphereAndMood(@Param("atmosphere") String atmosphere, @Param("moods") String[] moods);

    @Query(
        value = "SELECT * FROM musics WHERE atmosphere = :atmosphere AND mood && CAST(:moods AS varchar[])",
        nativeQuery = true
    )
    List<Music> findAllByAtmosphereAndMood(@Param("atmosphere") String atmosphere, @Param("moods") String[] moods);
}
