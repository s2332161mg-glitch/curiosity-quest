package com.hemhem.curiosity_quest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // このインターフェースがデータベース操作ツールであることを示す
public interface QuestRepository extends JpaRepository<Quest, Long> {

    // データベースから全てのクエストを作成順の逆（新しい順）で取得するメソッド
    List<Quest> findAllByOrderByIdDesc();
}