#!/bin/bash

sudo systemctl restart postgresql-9.4
sudo su postgres --command="psql < /vagrant/db/reset-development.sql"
exit $?

