package com.example.bishe.mapper;

import com.example.bishe.entity.SpecialWord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SpecialWordMapper {

    @Insert("INSERT INTO special_word(word, enabled, created_by) VALUES(#{word}, #{enabled}, #{createdBy})")
    @Options(useGeneratedKeys = true, keyProperty = "wordId")
    int insert(SpecialWord word);

    @Select("SELECT * FROM special_word ORDER BY word_id DESC")
    List<SpecialWord> findAll();

    @Select("SELECT * FROM special_word WHERE enabled = 1 ORDER BY word_id ASC")
    List<SpecialWord> findEnabled();

    @Select("SELECT * FROM special_word WHERE word_id = #{wordId}")
    SpecialWord findById(Long wordId);

    @Select("SELECT * FROM special_word WHERE word = #{word}")
    SpecialWord findByWord(String word);

    @Update("UPDATE special_word SET enabled = #{enabled} WHERE word_id = #{wordId}")
    int updateEnabled(@Param("wordId") Long wordId, @Param("enabled") int enabled);

    @Delete("DELETE FROM special_word WHERE word_id = #{wordId}")
    int deleteById(Long wordId);
}
