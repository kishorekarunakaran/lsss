package com.accakyra.lsss.lsm.data;

import com.accakyra.lsss.lsm.data.persistent.sst.SST;

import java.util.Set;

public interface Run extends Resource {

    void add(SST sst);

    Set<SST> getSstables();

    int size();
}