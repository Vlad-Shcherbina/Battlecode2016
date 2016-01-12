from __future__ import (
    division, absolute_import, print_function, unicode_literals)

import sys
import random
import copy

N = 20


def main():
    random.seed(42)

    mat = [['.'] * N for _ in range(N)]
    cd = [[0] * N for _ in range(N)]
    for i in range(N):
        mat[0][i] = mat[i][0] = mat[-1][i] = mat[i][-1] = '#'

    center_x = 1
    center_y = 1
    mat[7][1] = 'A'
    mat[7][7] = 'A'


    def try_move(i, j, multiplyer):
        grad_x = j - center_x
        grad_y = i - center_y
        d = (grad_x**2 + grad_y**2)**0.5
        if d > 0:
            grad_x /= d
            grad_y /= d

        res_pressure = max(res / 125, 0)

        WEIGHTS = {
            '.': 0,
            '#': 1,
            'T': 1, 'h': 1, 't': 1,
            'S': 0.9,
            'A': 1.8,
        }
        weight = WEIGHTS[mat[i][j]] * (8 - 1.3 - 0.1 * res_pressure**1.5)
        fx = 0
        fy = 0
        #fx = (1.3 + 0.1 * res_pressure**1.5 - 8) * grad_x
        #fy = (1.3 + 0.1 * res_pressure**1.5 - 8) * grad_y
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if dx == dy == 0:
                    continue
                d = (dx**2 + dy**2)**0.5

                #weight = .get(mat[i + dy][j + dx], 0)
                weight -= WEIGHTS[mat[i + dy][j + dx]]

                # fx -= grad_x * weight
                # fy -= grad_y * weight

                if mat[i + dy][j + dx] == '.':
                    fx += dx / d
                    fy += dy / d

                if mat[i][j] == mat[i + dy][j + dx] == 'S':
                    fx -= dx / d
                    fy -= dy / d

        fx -= grad_x * weight
        fy -= grad_y * weight

        for _ in range(8):
            while True:
                dx = random.randrange(-1, 2)
                dy = random.randrange(-1, 2)
                if dx == dy == 0:
                    continue
                break
            if mat[i + dy][j + dx] == '.':
                d = fx * dx + fy * dy
                if random.random() < multiplyer * d:
                    return i + dy, j + dx

        return None, None


    sieged = 0
    unsieged = 0

    old_mat = None

    res = 300
    for round in range(3000):
        if round == 2000:
            center_y = 1
        if old_mat != mat or round == 1999:
            print()
            print(round, int(res))
            for row in mat:
                print(' '.join(row))
            old_mat = copy.deepcopy(mat)

        locs = [(i, j) for i in range(N) for j in range(N)]
        random.shuffle(locs)
        for i, j in locs:
            cd[i][j] = max(cd[i][j] - 1, 0)
            if cd[i][j] >= 1:
                continue

            if mat[i][j] == 'A':
                i1, j1 = try_move(i, j, 0.05)
                if i1 != None:
                    mat[i1][j1] = 'A'
                    cd[i1][j1] = 2
                    mat[i][j] ='.'

            if mat[i][j] == 'A':
                i1 = i + random.randrange(-1, 2)
                j1 = j + random.randrange(-1, 2)
                if res >= 125 and mat[i1][j1] == '.':
                    res -= 125
                    mat[i1][j1] = random.choice('SSTTTTTTS')
                    cd[i][j] = cd[i1][j1] = 25

            if mat[i][j] == 'T':
                i1, j1 = try_move(i, j, 0.005)
                if i1 != None:
                    mat[i][j] = 't'
                    cd[i][j] = 10

            if mat[i][j] == 't':
                i1, j1 = try_move(i, j, 0.2)
                if i1 != None:
                    mat[i1][j1] = 'h'
                    cd[i1][j1] = 10
                    mat[i][j] = '.'

            if mat[i][j] == 'h':
                mat[i][j] = 'T'

            if mat[i][j] == 'S':
                i1, j1 = try_move(i, j, 0.05)
                if i1 != None:
                    mat[i1][j1] = 'S'
                    cd[i1][j1] = 2
                    mat[i][j] ='.'


        #cnt = sum(1 for row in mat for cell in row if cell not in '#.')
        cnt = 0
        for row in mat:
            for cell in row:
                if cell not in '.#':
                    cnt += 1
                if cell == 'T':
                    sieged += 1
                if cell in 'th':
                    unsieged += 1

        res += 2 * max(1 - 0.01 * cnt, 0)

    print(unsieged / (sieged + unsieged))


if __name__ == '__main__':
    main()
