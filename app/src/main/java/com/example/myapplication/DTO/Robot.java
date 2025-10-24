package com.example.myapplication.DTO;

import com.google.gson.annotations.SerializedName;

public class Robot {
    @SerializedName("id")
    private int id;
    @SerializedName("name")
    private String name;

    @SerializedName("status")
    private String status;

    public Robot(int id, String name, String status) {
        this.name = name;
        this.status = status;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
