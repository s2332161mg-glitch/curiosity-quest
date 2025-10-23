package com.hemhem.curiosity_quest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity // このクラスがデータベースのテーブル設計図であることを示す
public class Quest {

    @Id // このフィールドが主キー（一意なID）であることを示す
    @GeneratedValue(strategy = GenerationType.IDENTITY) // IDを自動で生成する設定
    private Long id;

    @Column(length = 1000) // 文字数の上限を設定
    private String question;

    @Column(columnDefinition = "TEXT") // 長いテキストを保存できるようにTEXT型を指定
    private String nodesJson;

    @Column(columnDefinition = "TEXT") // 長いテキストを保存できるようにTEXT型を指定
    private String edgesJson;
    

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getNodesJson() {
        return nodesJson;
    }

    public void setNodesJson(String nodesJson) {
        this.nodesJson = nodesJson;
    }

    public String getEdgesJson() {
        return edgesJson;
    }

    public void setEdgesJson(String edgesJson) {
        this.edgesJson = edgesJson;
    }
}