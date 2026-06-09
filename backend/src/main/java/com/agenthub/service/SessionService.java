package com.agenthub.service;

import com.agenthub.exception.BusinessException;
import com.agenthub.model.dto.ConversationVO;
import com.agenthub.model.dto.CreateConversationRequest;
import com.agenthub.model.entity.Agent;
import com.agenthub.model.entity.Conversation;
import com.agenthub.model.entity.ConversationParticipant;
import com.agenthub.model.entity.Message;
import com.agenthub.model.entity.User;
import com.agenthub.model.enums.ParticipantRole;
import com.agenthub.repository.AgentMapper;
import com.agenthub.repository.ConversationMapper;
import com.agenthub.repository.ConversationParticipantMapper;
import com.agenthub.repository.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private final ConversationMapper conversationMapper;
    private final ConversationParticipantMapper participantMapper;
    private final AgentMapper agentMapper;
    private final MessageMapper messageMapper;

    public SessionService(ConversationMapper conversationMapper,
                         ConversationParticipantMapper participantMapper,
                         AgentMapper agentMapper,
                         MessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.participantMapper = participantMapper;
        this.agentMapper = agentMapper;
        this.messageMapper = messageMapper;
    }

    @Transactional
    public ConversationVO createConversation(CreateConversationRequest request, User currentUser) {
        Conversation conversation = new Conversation();
        conversation.setOwnerId(currentUser.getId());

        if (request.isGroupChat()) {
            // Group chat with multiple agents
            conversation.setType("GROUP");
            List<Agent> agents = request.getAgentIds().stream()
                .map(agentMapper::selectById)
                .filter(a -> a != null && a.getEnabled())
                .collect(Collectors.toList());

            if (agents.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "No valid agents available");
            }

            conversation.setTitle(request.getTitle() != null ? request.getTitle() :
                "Group with " + agents.size() + " agents");
            conversationMapper.insert(conversation);

            // Add user as owner
            ConversationParticipant ownerParticipant = new ConversationParticipant();
            ownerParticipant.setConversationId(conversation.getId());
            ownerParticipant.setUserId(currentUser.getId());
            ownerParticipant.setRole(ParticipantRole.OWNER.name());
            participantMapper.insert(ownerParticipant);

            // Add all agents
            for (Agent agent : agents) {
                ConversationParticipant agentParticipant = new ConversationParticipant();
                agentParticipant.setConversationId(conversation.getId());
                agentParticipant.setAgentId(agent.getId());
                agentParticipant.setRole(ParticipantRole.MEMBER.name());
                participantMapper.insert(agentParticipant);
            }
        } else {
            // Single chat with one agent
            if (request.getAgentId() == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Agent ID is required");
            }
            Agent agent = agentMapper.selectById(request.getAgentId());
            if (agent == null || !agent.getEnabled()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Agent not available");
            }

            conversation.setType("SINGLE");
            conversation.setTitle(request.getTitle() != null ? request.getTitle() : "Chat with " + agent.getName());
            conversationMapper.insert(conversation);

            ConversationParticipant ownerParticipant = new ConversationParticipant();
            ownerParticipant.setConversationId(conversation.getId());
            ownerParticipant.setUserId(currentUser.getId());
            ownerParticipant.setRole(ParticipantRole.OWNER.name());
            participantMapper.insert(ownerParticipant);

            ConversationParticipant agentParticipant = new ConversationParticipant();
            agentParticipant.setConversationId(conversation.getId());
            agentParticipant.setAgentId(agent.getId());
            agentParticipant.setRole(ParticipantRole.MEMBER.name());
            participantMapper.insert(agentParticipant);
        }

        return buildConversationVO(conversation);
    }

    public List<ConversationVO> getUserConversations(Long userId) {
        LambdaQueryWrapper<ConversationParticipant> participantWrapper = new LambdaQueryWrapper<>();
        participantWrapper.eq(ConversationParticipant::getUserId, userId);
        List<ConversationParticipant> participations = participantMapper.selectList(participantWrapper);

        if (participations.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> conversationIds = participations.stream()
            .map(ConversationParticipant::getConversationId)
            .distinct()
            .collect(Collectors.toList());

        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Conversation::getId, conversationIds)
            .eq(Conversation::getArchived, false)  // 默认不显示归档会话
            .orderByDesc(Conversation::getPinned)
            .orderByDesc(Conversation::getUpdatedAt);
        List<Conversation> conversations = conversationMapper.selectList(wrapper);

        return conversations.stream()
            .map(this::buildConversationVO)
            .collect(Collectors.toList());
    }

    public List<ConversationVO> searchConversations(String keyword, Long userId) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getUserConversations(userId);
        }

        String searchPattern = "%" + keyword.trim().toLowerCase() + "%";

        // Find conversation IDs where title matches
        LambdaQueryWrapper<Conversation> titleWrapper = new LambdaQueryWrapper<>();
        titleWrapper.like(Conversation::getTitle, keyword.trim())
            .eq(Conversation::getArchived, false);
        List<Conversation> titleMatches = conversationMapper.selectList(titleWrapper);

        // Find message content matches
        LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.like(Message::getContent, keyword.trim())
            .eq(Message::getSenderType, "USER");
        List<Message> messageMatches = messageMapper.selectList(msgWrapper);

        // Get conversation IDs from message matches
        List<Long> messageConvIds = messageMatches.stream()
            .map(Message::getConversationId)
            .distinct()
            .collect(Collectors.toList());

        // Combine both results
        List<Long> allConvIds = new ArrayList<>();
        for (Conversation c : titleMatches) {
            if (!allConvIds.contains(c.getId())) {
                allConvIds.add(c.getId());
            }
        }
        for (Long convId : messageConvIds) {
            if (!allConvIds.contains(convId)) {
                allConvIds.add(convId);
            }
        }

        if (allConvIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Verify user has access to these conversations
        LambdaQueryWrapper<ConversationParticipant> participantWrapper = new LambdaQueryWrapper<>();
        participantWrapper.eq(ConversationParticipant::getUserId, userId)
            .in(ConversationParticipant::getConversationId, allConvIds);
        List<ConversationParticipant> participations = participantMapper.selectList(participantWrapper);

        List<Long> accessibleConvIds = participations.stream()
            .map(ConversationParticipant::getConversationId)
            .distinct()
            .collect(Collectors.toList());

        if (accessibleConvIds.isEmpty()) {
            return new ArrayList<>();
        }

        // Get conversations
        LambdaQueryWrapper<Conversation> convWrapper = new LambdaQueryWrapper<>();
        convWrapper.in(Conversation::getId, accessibleConvIds)
            .orderByDesc(Conversation::getPinned)
            .orderByDesc(Conversation::getUpdatedAt);
        List<Conversation> conversations = conversationMapper.selectList(convWrapper);

        return conversations.stream()
            .map(this::buildConversationVO)
            .collect(Collectors.toList());
    }

    public ConversationVO getConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, conversationId)
            .eq(ConversationParticipant::getUserId, userId);
        if (participantMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return buildConversationVO(conversation);
    }

    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        if (!conversation.getOwnerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only owner can delete conversation");
        }

        // Delete messages first (due to foreign key constraint)
        LambdaQueryWrapper<Message> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(Message::getConversationId, conversationId);
        messageMapper.delete(messageWrapper);

        // Delete participants
        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, conversationId);
        participantMapper.delete(wrapper);

        // Delete conversation
        conversationMapper.deleteById(conversationId);
    }

    @Transactional
    public void pinConversation(Long conversationId, Long userId, boolean pinned) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        if (!conversation.getOwnerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only owner can pin conversation");
        }

        conversation.setPinned(pinned);
        conversationMapper.updateById(conversation);
    }

    @Transactional
    public void archiveConversation(Long conversationId, Long userId, boolean archived) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        if (!conversation.getOwnerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only owner can archive conversation");
        }

        conversation.setArchived(archived);
        conversationMapper.updateById(conversation);
    }

    @Transactional
    public void updateConversationTitle(Long conversationId, Long userId, String title) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        if (!conversation.getOwnerId().equals(userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Only owner can update conversation");
        }

        conversation.setTitle(title);
        conversationMapper.updateById(conversation);
    }

    private ConversationVO buildConversationVO(Conversation conversation) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setType(conversation.getType());
        vo.setOwnerId(conversation.getOwnerId());
        vo.setPinned(conversation.getPinned() != null ? conversation.getPinned() : false);
        vo.setArchived(conversation.getArchived() != null ? conversation.getArchived() : false);
        vo.setCreatedAt(conversation.getCreatedAt());
        vo.setUpdatedAt(conversation.getUpdatedAt());

        LambdaQueryWrapper<ConversationParticipant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationParticipant::getConversationId, conversation.getId());
        List<ConversationParticipant> participants = participantMapper.selectList(wrapper);

        List<ConversationVO.ParticipantVO> participantVOs = new ArrayList<>();
        for (ConversationParticipant p : participants) {
            ConversationVO.ParticipantVO pv = new ConversationVO.ParticipantVO();
            pv.setId(p.getId());
            pv.setUserId(p.getUserId());
            pv.setAgentId(p.getAgentId());
            pv.setRole(p.getRole());

            if (p.getAgentId() != null) {
                Agent agent = agentMapper.selectById(p.getAgentId());
                if (agent != null) {
                    pv.setName(agent.getName());
                    pv.setAvatarUrl(agent.getAvatarUrl());
                    pv.setType("AGENT");
                }
            }
            participantVOs.add(pv);
        }
        vo.setParticipants(participantVOs);

        return vo;
    }
}
