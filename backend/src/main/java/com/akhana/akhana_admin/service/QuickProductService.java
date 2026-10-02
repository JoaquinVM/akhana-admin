package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.QuickProductGroupRequest;
import com.akhana.akhana_admin.dto.QuickProductGroupResponse;
import com.akhana.akhana_admin.dto.ReorderGroupsRequest;
import com.akhana.akhana_admin.dto.ReorderItemsRequest;

import java.util.List;
import java.util.UUID;

public interface QuickProductService {

    List<QuickProductGroupResponse> getAllGroups();

    QuickProductGroupResponse getGroupById(UUID id);

    QuickProductGroupResponse createGroup(QuickProductGroupRequest request);

    QuickProductGroupResponse updateGroup(UUID id, QuickProductGroupRequest request);

    void deleteGroup(UUID id);

    List<QuickProductGroupResponse> reorderGroups(ReorderGroupsRequest request);

    QuickProductGroupResponse reorderGroupItems(UUID groupId, ReorderItemsRequest request);
}
