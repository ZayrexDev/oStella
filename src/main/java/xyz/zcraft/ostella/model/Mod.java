package xyz.zcraft.ostella.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mod {
    private String acronym;
    private Map<String, Object> settings;
}
