drop table if exists users;
create table users (
  id integer primary key autoincrement,
  username text unique not null,
  email text unique,
  password_hash text not null,
  created_at timestamp default current_timestamp,
  last_login timestamp
);

drop table if exists entries;
create table entries (
  id integer primary key autoincrement,
  user_id integer not null,
  name text not null,
  type text not null,
  note text not null,
  created_at timestamp default current_timestamp,
  foreign key (user_id) references users (id) on delete cascade
);
