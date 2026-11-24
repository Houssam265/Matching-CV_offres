package com.gi3.matchingcv.dto;

import java.util.List;

public record ConversationDetailDto(ConversationDto conversation, List<MessageDto> messages) {}
