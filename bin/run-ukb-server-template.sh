# Runs the UKB docker microservice, so that the PageRank scoring resource will work.
# Expects docker to be installed and the following image pulled:
# docker pull johannpetrak/ms-ukb

docker run --name ms-ukb1 -it -p 9090:9090 -v /home/genevieve/yodie/yodie-pipeline/bio-yodie-resources/ukb:/ukbdata  johannpetrak/ms-ukb:latest  "run -n 1"
