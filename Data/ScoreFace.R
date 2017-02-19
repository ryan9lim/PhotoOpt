library("caret")

train <- read.csv("/Users/robertrtung/Documents/Yale Junior Year/HackNYU/Data/AnalysisData.csv", header = TRUE)

trainScore <- rep(NA, nrow(train))

for(i in 1:nrow(train)) {
  if(train[i,1] == 'good') {
    trainScore[i] = 1
  } else {
    trainScore[i] = 0
  }
}

trainScoreDF <- data.frame(score = trainScore)

# If NA, just give the average value
for(i in 3:10) {
  m <- mean(train[which(!is.na(train[,i])),i])
  train[which(is.na(train[,i])),i] <- m
}
#example1 <- data.frame(one = rnorm(50,0,1),
#                     two = rnorm(50,0,1),
#                     three = rnorm(50,0,1),
#                     four = rnorm(50,0,1),
#                     leftOpen = rnorm(50,0,1),
#                     rightOpen = rnorm(50,0,1),
#                     smiling = rnorm(50,0,1),
#                     score = runif(50, min=0, max=5))

modelFit <- pcaNNet(train[, 3:10], trainScoreDF[,"score"], size = 5, linout = TRUE, trace = FALSE)
modelFit

example2 <- data.frame(Type = c("good","bad"),
                      Index = c(1,2),
                      MouthHor = rnorm(2,0,1),
                      Cheeks = rnorm(2,0,1),
                      MouthVert = rnorm(2,0,1),
                      LeftEyeCheek = rnorm(2,0,1),
                      RightEyeCheek = rnorm(2,0,1),
                      LeftOpen = rnorm(2,0,1),
                      RightOpen = rnorm(2,0,1),
                      Smile = rnorm(2,0,1))

scoreIt = function(mh, ch, mv, lc, rc, lo, ro, sm) {
  dat <- data.frame(MouthHor = c(mh),
                         Cheeks = c(ch),
                         MouthVert = c(mv),
                         LeftEyeCheek = c(lc),
                         RightEyeCheek = c(rc),
                         LeftOpen = c(lo),
                         RightOpen = c(ro),
                         Smile = c(sm))
  return(predict(modelFit, dat[, 1:8]))
}

# predict(modelFit, example2[, 1:8])
# scoreIt(1,1,1,1,1,1,1,1)