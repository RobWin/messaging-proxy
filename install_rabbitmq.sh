#!/bin/bash
#oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:kube-system:default
helm install --namespace myproject --name msg --set image=rabbitmq:3.6.9-management-alpine,persistence.storageClass=generic,persistence.size=1Gi,rabbitmqUsername=guest,rabbitmqPassword=guest stable/rabbitmq