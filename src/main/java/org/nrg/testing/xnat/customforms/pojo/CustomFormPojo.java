package org.nrg.testing.xnat.customforms.pojo;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class CustomFormPojo {

    private List<AppliesTo> appliesToList;
    private String contents;
    private String formId;
    private String formUUID;
    private int formDisplayOrder;
    private String path;
    private String scope;
    private boolean doProjectsShareForm;
    private String username;
    private Date dateCreated;
    private boolean hasData;

}
