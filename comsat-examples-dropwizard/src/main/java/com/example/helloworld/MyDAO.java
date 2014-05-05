package com.example.helloworld;

import co.paralleluniverse.fibers.SuspendExecution;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;

public interface MyDAO {
    @SqlUpdate("create table something (id int primary key, name varchar(100))")
    void createSomethingTable() throws SuspendExecution;

    @SqlUpdate("insert into something (id, name) values (:id, :name)")
    void insert(@Bind("id") int id, @Bind("name") String name) throws SuspendExecution;

    @SqlQuery("select name from something where id = :id")
    String findNameById(@Bind("id") int id) throws SuspendExecution;
}
