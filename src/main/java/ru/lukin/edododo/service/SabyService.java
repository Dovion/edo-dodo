package ru.lukin.edododo.service;

import ru.lukin.edododo.dto.SabyAuthRequest;
import ru.lukin.edododo.model.ActDocument;

import java.util.Map;

public interface SabyService {
    Map<String, Object> authenticate(SabyAuthRequest authRequest);

    Map<String, Object> sendActToSaby(ActDocument act);
}