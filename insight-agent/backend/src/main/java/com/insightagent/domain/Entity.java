package com.insightagent.domain;

/** Named entity mentioned in the article. {@code type} is loose (PERSON / ORG / EVENT / PLACE / ...). */
public record Entity(String name, String type) {}
