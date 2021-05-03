package com.xxl.job.core;

import org.springframework.cglib.core.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

public class Test {

    public static void main(String[] args) {
        List<User> newUser = new ArrayList<>();
        User user1 = new User();
        user1.setId(1);
        user1.setName("n1");

        newUser.add(user1);

        User user2 = new User();
        user2.setId(2);
        user2.setName("n2");
        newUser.add(user2);

        User user3 = new User();
        user3.setId(3);
        user3.setName("n3");
        newUser.add(user3);

        List<User> oldUser = new ArrayList<>();
        User old1 = new User();
        old1.setId(1);
        old1.setName("n1");
        oldUser.add(old1);

        User old2 = new User();
        old2.setId(2);
        old2.setName("n2");
        oldUser.add(old2);

        Map<Long, List<String>> collect = newUser.stream().collect(Collectors.groupingBy(User::getId, Collectors.mapping(User::getName, Collectors.toList())));

        newUser.addAll(oldUser);
        System.out.println(newUser);


        Set<User> collect1 = newUser.stream().collect(Collectors.toSet());


    }



    static class User {
        long id;

        String name;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return id == user.id && name.equals(user.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }

        @Override
        public String toString() {
            return "User{" +
                    "id ='" + id + '\'' +
                    ", name =" + name +
                    '}';
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

}
