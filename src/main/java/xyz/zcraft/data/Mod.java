package xyz.zcraft.data;

import lombok.Data;

import java.util.Map;

@Data
public class Mod {
    private String acronym;
    private Map<String, Object> settings;
}
